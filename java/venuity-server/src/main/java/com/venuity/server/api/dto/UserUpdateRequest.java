package com.venuity.server.api.dto;

import com.venuity.server.domain.Role;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UserUpdateRequest(
        @Size(max = 255) String fullName,
        @Size(max = 254) String email,
        @Size(max = 30) String phone,
        Role role,
        String notes,
        Instant accountExpiresAt
) {
}
