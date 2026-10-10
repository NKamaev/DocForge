package com.docforge.template.dto;

public record SaveVersionResponse(
        TemplateVersionDTO version,
        TemplateResponse template
) {}
