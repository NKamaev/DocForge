package com.docforge.document.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentMemberDTO(
        UUID userId,
        String userLogin,
        String userDisplayName,
        String role,
        String source,
        Instant grantedAt
) {}
