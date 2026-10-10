package com.docforge.template.dto;

public record RollbackResponse(
        TemplateVersionDTO version,
        TemplateResponse template
) {}
