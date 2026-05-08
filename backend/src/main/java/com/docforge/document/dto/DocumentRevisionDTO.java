package com.docforge.document.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentRevisionDTO(
        UUID id,
        UUID documentId,
        int revisionNumber,
        UUID savedBy,
        OffsetDateTime savedAt,
        String comment
) {}
