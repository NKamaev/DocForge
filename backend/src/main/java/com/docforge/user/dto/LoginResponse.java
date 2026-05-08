package com.docforge.user.dto;

import java.util.UUID;

public record LoginResponse(
        String token,
        UUID userId,
        String login,
        String displayName,
        boolean admin
) {}
