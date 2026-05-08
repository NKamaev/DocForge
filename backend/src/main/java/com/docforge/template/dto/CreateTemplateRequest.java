package com.docforge.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateTemplateRequest(
        @NotBlank @Size(max = 500) String name,
        String description,
        @Size(max = 100) String category,
        UUID defaultFolderId
) {}
