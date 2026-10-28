package com.venuity.server.api.dto;

public record BrandingResponse(
        String businessName,
        String primaryColor,
        String primaryHoverColor,
        String logoUrl,
        String appTitle
) {
}
