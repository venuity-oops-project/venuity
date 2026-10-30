package com.venuity.server.api.dto;

import java.util.List;

public record UserDetailResponse(
        UserResponse user,
        UserActivityResponse activity,
        int activeSessionsCount,
        List<AuditLogResponse> recentAuditLogs
) {
}
