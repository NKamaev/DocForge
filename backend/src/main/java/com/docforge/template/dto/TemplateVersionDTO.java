package com.docforge.template.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TemplateVersionDTO(
        UUID id,
        UUID templateId,
        int versionNumber,
        OffsetDateTime createdAt,
        UUID createdBy,
        String comment
) {}
