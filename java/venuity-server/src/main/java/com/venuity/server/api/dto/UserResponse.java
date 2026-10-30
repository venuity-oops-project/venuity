package com.venuity.server.api.dto;

import java.time.Instant;

public record UserResponse(
        Long id,
        String publicId,
        String username,
        String role,
        String fullName,
        String email,
        String phone,
        String notes,
        String status,
        boolean enabled,
        boolean mustChangePassword,
        int failedLoginAttempts,
        Instant lockedUntil,
        Instant accountExpiresAt,
        Instant lastLogin,
        Instant dateJoined,
        Instant updatedAt,
        Long createdBy
) {
    public UserResponse(String publicId, String username, String role, String email,
                        String firstName, String lastName, Instant lastLogin, Instant dateJoined) {
        this(null, publicId, username, role,
                (firstName != null || lastName != null ? ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim() : username),
                email, null, null, "ACTIVE", true, false, 0, null, null, lastLogin, dateJoined, null, null);
    }
}
