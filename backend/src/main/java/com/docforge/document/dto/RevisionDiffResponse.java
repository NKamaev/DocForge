package com.docforge.document.dto;

import java.util.UUID;

public record RevisionDiffResponse(
        UUID revisionId,
        int revisionNumber,
        Integer compareToRevisionNumber,
        boolean compareToCurrent,
        Object targetBody,
        Object compareBody
) {}
