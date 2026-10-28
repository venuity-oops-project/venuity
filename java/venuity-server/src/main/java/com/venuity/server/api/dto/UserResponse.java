package com.venuity.server.api.dto;

import java.time.Instant;

public record UserResponse(
        String publicId,
        String username,
        String role,
        String email,
        String firstName,
        String lastName,
        Instant lastLogin,
        Instant dateJoined
) {
}
