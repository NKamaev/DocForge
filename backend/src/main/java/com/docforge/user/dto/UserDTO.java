package com.docforge.user.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserDTO(
        UUID id,
        String login,
        String displayName,
        String email,
        boolean admin,
        OffsetDateTime createdAt
) {}
