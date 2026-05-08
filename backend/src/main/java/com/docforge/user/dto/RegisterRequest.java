package com.docforge.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 100) String login,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank @Size(max = 255) String displayName,
        String email
) {}
