package com.docforge.document.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentParticipantDTO(
        UUID userId,
        String login,
        String displayName,
        String role,
        OffsetDateTime joinedAt
) {}
