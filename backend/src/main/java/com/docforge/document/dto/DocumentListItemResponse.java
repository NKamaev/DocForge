package com.docforge.document.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentListItemResponse(
        UUID id,
        String name,
        String status,
        UUID templateId,
        UUID folderId,
        Instant createdAt
) {}
