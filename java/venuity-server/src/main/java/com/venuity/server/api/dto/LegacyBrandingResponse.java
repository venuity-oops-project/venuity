package com.venuity.server.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LegacyBrandingResponse(
        @JsonProperty("business_name") String businessName,
        @JsonProperty("primary_color") String primaryColor,
        @JsonProperty("primary_hover_color") String primaryHoverColor,
        @JsonProperty("logo_url") String logoUrl,
        @JsonProperty("app_title") String appTitle
) {
}
