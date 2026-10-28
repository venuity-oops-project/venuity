package com.venuity.server.api.dto;

import com.venuity.server.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank(message = "Username is required")
        @Size(max = 150, message = "Username must not exceed 150 characters")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 4, max = 128, message = "Password must be at least 4 characters")
        String password,

        Role role,
        String email,
        String firstName,
        String lastName
) {
}

