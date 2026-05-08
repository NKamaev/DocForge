package com.docforge.directory.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DirectoryListItemResponse(
        UUID id,
        String name,
        List<DirectoryFieldDto> fields,
        int entryCount,
        Instant createdAt,
        Instant updatedAt
) {}
