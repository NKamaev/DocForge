package com.docforge.document.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentRevisionDTO(
        UUID id,
        UUID documentId,
        int editionNumber,
        UUID savedBy,
        String authorName,
        OffsetDateTime savedAt,
        String comment
) {}
