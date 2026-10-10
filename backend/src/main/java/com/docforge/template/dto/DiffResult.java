package com.docforge.template.dto;

import java.util.List;

public record DiffResult(
        String summary,
        List<String> changes,
        boolean hasChanges
) {}
