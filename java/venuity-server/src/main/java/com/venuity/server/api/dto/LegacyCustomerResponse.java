package com.venuity.server.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LegacyCustomerResponse(
        @JsonProperty("public_id") String publicId,
        String name,
        String balance,
        @JsonProperty("qr_id") String qrId,
        @JsonProperty("created_at") String createdAt
) {
}
