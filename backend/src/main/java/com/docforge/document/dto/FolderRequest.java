package com.docforge.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record FolderRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 50) String color,
        UUID parentId
) {}
