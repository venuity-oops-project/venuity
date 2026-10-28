package com.venuity.server.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CustomerCreateRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 30) String phone,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal initialBalance,
        @Size(max = 64) String publicCode
) {
}
