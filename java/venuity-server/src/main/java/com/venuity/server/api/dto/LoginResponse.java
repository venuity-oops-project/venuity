package com.venuity.server.api.dto;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        UserResponse user
) {
}
