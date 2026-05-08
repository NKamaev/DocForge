package com.docforge.document.dto;

import java.time.Instant;
import java.util.UUID;

public record FolderMemberDTO(
        UUID userId,
        String userLogin,
        String userDisplayName,
        String role,
        Instant grantedAt
) {}
