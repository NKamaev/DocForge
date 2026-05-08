package com.docforge.document.generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PythonConditionEvaluator}.
 *
 * <p><b>Runtime requirement:</b> these tests need GraalVM JDK or GraalPy on
 * the classpath. On a plain JDK without GraalPython the first test will fail
 * with "A language with id 'python' is not installed" — install GraalVM JDK
 * or add the graalpy standalone distribution to the runtime classpath.
 */
class PythonConditionEvaluatorTest {

    private PythonConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new PythonConditionEvaluator();
    }

    // ── Basic execution ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Basic script execution")
    class BasicExecution {

        @Test
        @DisplayName("'result = True' → true")
        void literalTrue() {
            assertTrue(evaluator.evaluate("result = True", Map.of()));
        }

        @Test
        @DisplayName("'result = False' → false")
        void literalFalse() {
            assertFalse(evaluator.evaluate("result = False", Map.of()));
        }

        @Test
        @DisplayName("blank script → true (no restriction)")
        void blankScript() {
            assertTrue(evaluator.evaluate("", Map.of()));
            assertTrue(evaluator.evaluate(null, Map.of()));
        }
    }

    // ── Variable bindings ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Answer variable bindings")
    class VariableBindings {

        @Test
        @DisplayName("'result = x > 10' → true when x=15")
        void numericGreaterThan() {
            assertTrue(evaluator.evaluate("result = x > 10", Map.of("x", 15)));
        }

        @Test
        @DisplayName("'result = x > 10' → false when x=5")
        void numericGreaterThanFalse() {
            assertFalse(evaluator.evaluate("result = x > 10", Map.of("x", 5)));
        }

        @Test
        @DisplayName("boolean answer: 'result = has_penalty' → true")
        void booleanBinding() {
            assertTrue(evaluator.evaluate("result = has_penalty", Map.of("has_penalty", true)));
        }

        @Test
        @DisplayName("boolean answer: 'result = has_penalty' → false")
        void booleanBindingFalse() {
            assertFalse(evaluator.evaluate("result = has_penalty", Map.of("has_penalty", false)));
        }

        @Test
        @DisplayName("string comparison: 'result = contract_type == \"Подряд\"'")
        void stringComparison() {
            Map<String, Object> answers = Map.of("contract_type", "Подряд");
            assertTrue(evaluator.evaluate("result = contract_type == 'Подряд'", answers));
        }

        @Test
        @DisplayName("multi-variable script")
        void multiVariable() {
            Map<String, Object> answers = Map.of("has_penalty", true, "penalty_percent", 15);
            assertTrue(evaluator.evaluate(
                "result = has_penalty and penalty_percent > 10", answers));
        }

        @Test
        @DisplayName("null answer is omitted — NameError becomes ScriptExecutionException")
        void nullAnswerOmitted() {
            // null values are not bound → NameError in Python → ScriptExecutionException
            java.util.Map<String, Object> answers = new java.util.HashMap<>();
            answers.put("x", null);
            assertThrows(ScriptExecutionException.class,
                () -> evaluator.evaluate("result = x > 0", answers));
        }

        @Test
        @DisplayName("double (floating-point) binding: 'result = rate >= 0.5' when rate=0.75")
        void doubleBinding() {
            assertTrue(evaluator.evaluate("result = rate >= 0.5", Map.of("rate", 0.75)));
        }
    }

    // ── Multi-line scripts ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Multi-line scripts")
    class MultiLineScripts {

        @Test
        @DisplayName("if/else branching")
        void ifElseBranching() {
            Map<String, Object> answers = Map.of("contract_type", "Подряд", "has_penalty", true);
            String script = """
                if contract_type == 'Подряд':
                    result = has_penalty
                else:
                    result = False
                """;
            assertTrue(evaluator.evaluate(script, answers));
        }

        @Test
        @DisplayName("if/else — false branch")
        void ifElseFalseBranch() {
            Map<String, Object> answers = Map.of("contract_type", "Аренда", "has_penalty", true);
            String script = """
                if contract_type == 'Подряд':
                    result = has_penalty
                else:
                    result = False
                """;
            assertFalse(evaluator.evaluate(script, answers));
        }

        @Test
        @DisplayName("arithmetic in script")
        void arithmeticScript() {
            Map<String, Object> answers = Map.of("base", 100, "factor", 3);
            String script = """
                total = base * factor
                result = total > 200
                """;
            assertTrue(evaluator.evaluate(script, answers));
        }
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("syntax error → ScriptExecutionException")
        void syntaxError() {
            ScriptExecutionException ex = assertThrows(ScriptExecutionException.class,
                () -> evaluator.evaluate("def (broken syntax", Map.of()));
            assertTrue(ex.getMessage().contains("syntax") || ex.getMessage().contains("error"),
                "Expected message to mention syntax/error, got: " + ex.getMessage());
        }

        @Test
        @DisplayName("runtime error (division by zero) → ScriptExecutionException")
        void runtimeError() {
            assertThrows(ScriptExecutionException.class,
                () -> evaluator.evaluate("result = 1 / 0 > 0", Map.of()));
        }

        @Test
        @DisplayName("missing 'result' variable → ScriptExecutionException")
        void missingResultVariable() {
            ScriptExecutionException ex = assertThrows(ScriptExecutionException.class,
                () -> evaluator.evaluate("x = 42", Map.of()));
            assertTrue(ex.getMessage().contains("result"),
                "Expected message to mention 'result', got: " + ex.getMessage());
        }

        @Test
        @DisplayName("non-boolean 'result' → ScriptExecutionException")
        void nonBooleanResult() {
            ScriptExecutionException ex = assertThrows(ScriptExecutionException.class,
                () -> evaluator.evaluate("result = 'yes'", Map.of()));
            assertTrue(ex.getMessage().contains("result"),
                "Expected message to mention 'result', got: " + ex.getMessage());
        }
    }

    // ── Timeout ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Timeout enforcement")
    class TimeoutEnforcement {

        @Test
        @Timeout(8)  // JUnit wall-clock guard: 2s internal + generous overhead
        @DisplayName("infinite loop → ScriptExecutionException (within 2s)")
        void infiniteLoop() {
            ScriptExecutionException ex = assertThrows(ScriptExecutionException.class,
                () -> evaluator.evaluate("while True: pass", Map.of()));
            assertTrue(ex.getMessage().contains("timed out") || ex.getMessage().contains("timeout"),
                "Expected timeout message, got: " + ex.getMessage());
        }

        @Test
        @Timeout(8)
        @DisplayName("CPU-intensive loop → times out")
        void cpuIntensiveLoop() {
            String script = """
                total = 0
                while True:
                    total += 1
                result = total > 0
                """;
            assertThrows(ScriptExecutionException.class,
                () -> evaluator.evaluate(script, Map.of()));
        }
    }

    // ── Sandbox restrictions ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Sandbox restrictions")
    class SandboxRestrictions {

        @Test
        @DisplayName("importing os module is restricted")
        void importOsRestricted() {
            // io=false means no file-system; 'os' module may partially load
            // but file operations must fail
            assertThrows(ScriptExecutionException.class,
                () -> evaluator.evaluate("import os\nos.listdir('/')\nresult = True", Map.of()));
        }
    }
}
