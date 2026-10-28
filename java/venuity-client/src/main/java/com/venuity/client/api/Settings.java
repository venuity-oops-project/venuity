package com.venuity.client.api;

import java.math.BigDecimal;

public record Settings(
        String businessName,
        String currencySymbol,
        BigDecimal checkinFee,
        String primaryColor,
        String primaryHoverColor,
        String logoUrl,
        String appTitle,
        String businessTimezone
) {

    public String displayBusinessName() {
        return businessName == null || businessName.isBlank() ? "Venuity" : businessName;
    }

    public String displayCurrencySymbol() {
        return currencySymbol == null || currencySymbol.isBlank() ? "Rs" : currencySymbol;
    }
}
