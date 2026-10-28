package com.venuity.server.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LegacyRechargeRequest(
        @JsonProperty("customer_id") String customerId,
        String amount
) {
}
