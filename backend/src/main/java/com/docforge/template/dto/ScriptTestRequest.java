package com.docforge.template.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record ScriptTestRequest(
        @NotBlank String scriptId,
        Map<String, Object> mockAnswers
) {}
