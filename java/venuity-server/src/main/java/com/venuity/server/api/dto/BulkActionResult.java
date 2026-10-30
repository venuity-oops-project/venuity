package com.venuity.server.api.dto;

import java.util.List;

public record BulkActionResult(
        int total,
        int succeeded,
        int failed,
        List<RowResult> results
) {
    public record RowResult(
            Long userId,
            boolean success,
            String message
    ) {
    }
}
