package com.docforge.common.exception;

import java.util.Map;

/**
 * Thrown when business-level validation of user-supplied answers fails
 * (missing required fields, wrong types, violated constraints).
 *
 * <p>Carries a map of {@code fieldCode → errorMessage} pairs so the caller
 * can surface precise per-field errors to the client.
 */
public class ValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
