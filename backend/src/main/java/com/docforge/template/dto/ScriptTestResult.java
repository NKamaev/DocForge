package com.docforge.template.dto;

public record ScriptTestResult(
        boolean success,
        Boolean result,
        String error,
        long executionMs
) {}
