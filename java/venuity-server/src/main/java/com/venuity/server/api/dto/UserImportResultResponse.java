package com.venuity.server.api.dto;

import java.util.List;

public record UserImportResultResponse(
        int totalImported,
        List<ImportedUserCredential> createdUsers
) {
    public record ImportedUserCredential(
            String username,
            String fullName,
            String role,
            String temporaryPassword
    ) {
    }
}
