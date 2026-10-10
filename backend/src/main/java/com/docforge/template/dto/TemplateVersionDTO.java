package com.docforge.template.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TemplateVersionDTO(
        UUID id,
        UUID templateId,
        int versionNumber,
        int majorVersion,
        int minorVersion,
        OffsetDateTime createdAt,
        UUID createdBy,
        String comment,
        String bumpType,
        String autoDescription,
        Integer rollbackFrom
) {}
