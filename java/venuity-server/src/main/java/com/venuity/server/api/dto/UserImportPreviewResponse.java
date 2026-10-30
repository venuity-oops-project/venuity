package com.venuity.server.api.dto;

import java.util.List;

public record UserImportPreviewResponse(
        int totalRows,
        int validRows,
        int invalidRows,
        List<PreviewRow> rows
) {
    public record PreviewRow(
            int lineNumber,
            String username,
            String fullName,
            String email,
            String phone,
            String role,
            boolean valid,
            String errorReason
    ) {
    }
}
