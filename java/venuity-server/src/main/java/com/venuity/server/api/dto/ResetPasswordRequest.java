package com.venuity.server.api.dto;

public record ResetPasswordRequest(
        String password,
        Boolean generatePassword
) {
}
