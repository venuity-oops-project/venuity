package com.venuity.server.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LegacySettingsResponse(
        @JsonProperty("business_name") String businessName,
        @JsonProperty("currency_symbol") String currencySymbol,
        @JsonProperty("checkin_fee") String checkinFee,
        @JsonProperty("primary_color") String primaryColor,
        @JsonProperty("primary_hover_color") String primaryHoverColor,
        @JsonProperty("logo_url") String logoUrl,
        @JsonProperty("app_title") String appTitle
) {
}
