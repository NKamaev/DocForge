package com.docforge.template.dto;

import com.docforge.template.model.DocumentFormatting;
import com.docforge.template.model.Parameter;
import com.docforge.template.model.Phrasing;
import com.docforge.template.model.Questionnaire;
import com.docforge.template.model.Script;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UpdateTemplateRequest(
        @NotBlank @Size(max = 500) String name,
        String description,
        @Size(max = 100) String category,
        List<Parameter> parameters,
        Questionnaire questionnaire,
        Map<String, Object> body,
        List<Script> scripts,
        DocumentFormatting formatting,
        List<Phrasing> phrasings,
        UUID defaultFolderId
) {}
