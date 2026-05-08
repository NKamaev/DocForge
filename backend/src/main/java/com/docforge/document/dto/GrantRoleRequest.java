package com.docforge.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GrantRoleRequest(
        @NotNull UUID userId,
        @NotBlank String role
) {}
