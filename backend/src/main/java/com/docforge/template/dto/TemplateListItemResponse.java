package com.docforge.template.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TemplateListItemResponse(
        UUID id,
        String code,
        String name,
        String description,
        String category,
        boolean published,
        OffsetDateTime createdAt
) {}
