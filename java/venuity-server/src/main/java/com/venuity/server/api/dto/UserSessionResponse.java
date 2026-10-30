package com.venuity.server.api.dto;

import java.time.Instant;

public record UserSessionResponse(
        Long id,
        String ip,
        String userAgent,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        Instant revokedAt,
        boolean current
) {
}
