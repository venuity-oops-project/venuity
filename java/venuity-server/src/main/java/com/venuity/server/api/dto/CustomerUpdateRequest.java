package com.venuity.server.api.dto;

import jakarta.validation.constraints.Size;

public record CustomerUpdateRequest(
        @Size(max = 150) String name,
        @Size(max = 30) String phone
) {
}
