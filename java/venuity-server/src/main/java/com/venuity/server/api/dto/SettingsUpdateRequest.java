package com.venuity.server.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SettingsUpdateRequest(
        @Size(max = 150) String businessName,
        @Size(max = 8) String currencySymbol,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal checkinFee,
        @Size(max = 7) String primaryColor,
        @Size(max = 7) String primaryHoverColor,
        @Size(max = 255) String logoUrl,
        @Size(max = 150) String appTitle,
        @Size(max = 64) String businessTimezone
) {
}
