package com.venuity.client.api;

import java.time.Instant;

public record User(
        String publicId,
        String username,
        String role,
        String email,
        String firstName,
        String lastName,
        Instant lastLogin,
        Instant dateJoined
) {

    public String displayName() {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String full = (first + " " + last).trim();
        return full.isBlank() ? username : full;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
