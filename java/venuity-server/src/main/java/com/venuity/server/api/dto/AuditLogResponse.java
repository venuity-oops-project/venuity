package com.venuity.server.api.dto;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Instant occurredAt,
        Long actorUserId,
        String actorUsername,
        String action,
        Long targetUserId,
        String targetUsername,
        String ip,
        String details,
        boolean success
) {
}
