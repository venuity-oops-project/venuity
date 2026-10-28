package com.venuity.client.api;

import java.time.Instant;

public record Session(
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        User user
) {
}
