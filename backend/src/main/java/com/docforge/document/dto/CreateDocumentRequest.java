package com.docforge.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateDocumentRequest(
        @NotNull UUID templateId,
        @NotBlank String name,
        UUID folderId,
        Integer templateVersionNumber
) {}
