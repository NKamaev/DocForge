package com.docforge.directory.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DirectoryResponse(
        UUID id,
        String name,
        List<DirectoryFieldDto> fields,
        List<DirectoryEntryDto> entries,
        Instant createdAt,
        Instant updatedAt
) {}
