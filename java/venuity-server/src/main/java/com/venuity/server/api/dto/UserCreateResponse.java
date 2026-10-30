package com.venuity.server.api.dto;

public record UserCreateResponse(
        UserResponse user,
        String temporaryPassword
) {
}
