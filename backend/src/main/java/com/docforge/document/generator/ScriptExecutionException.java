package com.docforge.document.generator;

/**
 * Thrown when a Python script fails to execute — due to a syntax error,
 * runtime exception, timeout, or a missing {@code result} variable.
 */
public class ScriptExecutionException extends RuntimeException {

    public ScriptExecutionException(String message) {
        super(message);
    }

    public ScriptExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
