package com.venuity.server.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LegacyCustomerCreateRequest(
        String name,
        @JsonProperty("initial_balance") String initialBalance,
        @JsonProperty("qr_id") String qrId
) {
}
