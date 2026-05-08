package com.docforge.document.dto;

import java.util.UUID;

public record FolderResponse(
        UUID id,
        String name,
        String color,
        long documentCount,
        UUID parentId
) {}
