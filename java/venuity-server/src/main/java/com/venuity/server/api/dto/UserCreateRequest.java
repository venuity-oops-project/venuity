package com.venuity.server.api.dto;

import com.venuity.server.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UserCreateRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 32, message = "Username must be between 3 and 32 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, digits, dots, underscores, and hyphens")
        String username,

        String password,

        Role role,

        String fullName,

        String email,

        String phone,

        String notes,

        Instant accountExpiresAt,

        Boolean generatePassword,

        Boolean mustChangePassword,

        // Legacy fields for backwards compatibility
        String firstName,
        String lastName
) {
    public UserCreateRequest(String username, String password, Role role, String email, String firstName, String lastName) {
        this(username, password, role, null, email, null, null, null, false, false, firstName, lastName);
    }
}
