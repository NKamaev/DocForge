package com.docforge.template.dto;

import com.docforge.template.model.DocumentFormatting;
import com.docforge.template.model.Parameter;
import com.docforge.template.model.Phrasing;
import com.docforge.template.model.Questionnaire;
import com.docforge.template.model.Script;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.UUID;

/**
 * Полный шаблон: метаданные из PostgreSQL (template_registry) + тело из MongoDB (templates).
 */
public record TemplateResponse(
        // ── Метаданные (PostgreSQL) ──
        UUID id,
        String code,
        String name,
        String description,
        String category,
        int version,
        boolean published,
        UUID ownerId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        // ── Папка по умолчанию ──
        UUID defaultFolderId,
        // ── Тело шаблона (MongoDB) ──
        List<Parameter> parameters,
        Questionnaire questionnaire,
        Map<String, Object> body,
        DocumentFormatting formatting,
        List<Script> scripts,
        List<Phrasing> phrasings
) {}
