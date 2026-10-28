package com.venuity.server.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LegacyCheckInRequest(
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("qr_id") String qrId
) {
}
