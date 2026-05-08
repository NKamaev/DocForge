package com.docforge.document.dto;

import java.util.UUID;

public record GenerateResponse(
        UUID documentId,
        Object body
) {}
