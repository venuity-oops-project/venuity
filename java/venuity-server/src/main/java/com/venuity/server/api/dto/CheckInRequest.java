package com.venuity.server.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckInRequest(@NotBlank String code) {
}
