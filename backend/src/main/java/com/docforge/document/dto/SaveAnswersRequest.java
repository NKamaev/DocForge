package com.docforge.document.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record SaveAnswersRequest(
        @NotNull Map<String, Object> answers
) {}
