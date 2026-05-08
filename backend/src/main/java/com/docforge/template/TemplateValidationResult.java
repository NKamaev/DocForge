package com.docforge.template;

import java.util.List;

public record TemplateValidationResult(boolean valid, List<String> errors) {}
