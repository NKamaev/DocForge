package com.docforge.document.generator;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Evaluates Python-script conditions (type: "PYTHON") in a GraalPython sandbox.
 *
 * <p>The script receives all questionnaire answers as top-level variables
 * and must assign a boolean to {@code result}, e.g.:
 * <pre>
 *   result = has_penalty and penalty_percent > 10
 * </pre>
 *
 * <p><b>Sandbox restrictions (no way out):</b>
 * <ul>
 *   <li>No file-system access ({@code allowIO = false})</li>
 *   <li>No native calls ({@code allowNativeAccess = false})</li>
 *   <li>No thread creation ({@code allowCreateThread = false})</li>
 *   <li>No Java class access ({@code allowHostAccess = NONE})</li>
 *   <li>No external Python modules ({@code python.PythonPath = ""})</li>
 *   <li>Hard 2-second wall-clock timeout</li>
 * </ul>
 *
 * <p><b>Requires GraalVM JDK or GraalPy standalone</b> on the classpath.
 * On a plain JVM this class will throw at call time, not at construction time.
 */
@Component
public class PythonConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(PythonConditionEvaluator.class);
    private static final long TIMEOUT_SECONDS = 2;

    /**
     * Single daemon thread shared across all evaluations — only fires
     * {@code context.close(true)} on timeout; does no heavy lifting.
     */
    private final ScheduledExecutorService timeoutScheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "python-timeout");
            t.setDaemon(true);
            return t;
        });

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs {@code script} with the given {@code answers} as bindings.
     *
     * @param script  Python source code; must assign a bool to {@code result}
     * @param answers questionnaire answers (null values are skipped)
     * @return value of {@code result} after execution
     * @throws ScriptExecutionException on timeout, runtime error, syntax error,
     *                                  or missing/non-boolean {@code result}
     */
    public boolean evaluate(String script, Map<String, Object> answers) {
        if (script == null || script.isBlank()) {
            return true;
        }

        Context context = null;
        ScheduledFuture<?> canceller = null;
        try {
            context = buildContext(answers);

            // Schedule a hard cancel from the timeout thread
            Context finalCtx = context;
            canceller = timeoutScheduler.schedule(
                () -> {
                    log.warn("Python script timed out — forcing context close");
                    finalCtx.close(true);
                },
                TIMEOUT_SECONDS, TimeUnit.SECONDS
            );

            context.eval("python", script);
            canceller.cancel(false);

            Value result = context.getBindings("python").getMember("result");
            if (result == null || result.isNull()) {
                throw new ScriptExecutionException(
                    "Script completed without assigning 'result' variable");
            }
            if (!result.isBoolean()) {
                throw new ScriptExecutionException(
                    "'result' must be a boolean, got: " + result);
            }
            return result.asBoolean();

        } catch (PolyglotException e) {
            if (canceller != null) canceller.cancel(false);
            if (e.isCancelled()) {
                throw new ScriptExecutionException(
                    "Python script timed out after " + TIMEOUT_SECONDS + " seconds");
            }
            if (e.isSyntaxError()) {
                throw new ScriptExecutionException(
                    "Python script syntax error: " + e.getMessage(), e);
            }
            throw new ScriptExecutionException(
                "Python script runtime error: " + e.getMessage(), e);

        } finally {
            if (context != null) {
                try { context.close(); } catch (Exception ignored) {}
            }
        }
    }

    // ── Context factory ───────────────────────────────────────────────────────

    private Context buildContext(Map<String, Object> answers) {
        Context context = Context.newBuilder("python")
            .allowIO(IOAccess.NONE)
            .allowNativeAccess(false)
            .allowCreateThread(false)
            .allowHostAccess(HostAccess.NONE)
            .option("python.PythonPath", "")
            .build();

        if (answers != null) {
            Value bindings = context.getBindings("python");
            answers.forEach((key, value) -> {
                if (value != null) {
                    bindings.putMember(key, value);
                }
                // null answers are simply absent — NameError in the script
                // is a script author's problem, not our invariant
            });
        }

        return context;
    }
}
