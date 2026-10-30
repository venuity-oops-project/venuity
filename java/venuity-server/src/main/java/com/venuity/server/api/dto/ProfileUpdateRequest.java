package com.venuity.server.api.dto;

import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @Size(max = 255) String fullName,
        @Size(max = 254) String email,
        @Size(max = 30) String phone
) {
}
