package com.venuity.server.api.dto;

import java.math.BigDecimal;

public record SettingsResponse(
        String businessName,
        String currencySymbol,
        BigDecimal checkinFee,
        String primaryColor,
        String primaryHoverColor,
        String logoUrl,
        String appTitle,
        String businessTimezone
) {
}
