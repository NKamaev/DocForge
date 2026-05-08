package com.docforge.document.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Полный ответ на GET /api/documents/{id} и POST /api/documents.
 * questionnaire — null при getById; заполняется при create.
 */
public record DocumentResponse(
        UUID id,
        String name,
        String status,
        UUID templateId,
        UUID templateSnapshotId,
        Integer templateVersionNumber,
        UUID ownerId,
        UUID folderId,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> answers,
        Object generatedBody,
        Object currentBody,
        QuestionnaireResponse questionnaire
) {}
