package com.docforge.document.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddParticipantRequest(
        @NotNull UUID userId,
        String role
) {}
