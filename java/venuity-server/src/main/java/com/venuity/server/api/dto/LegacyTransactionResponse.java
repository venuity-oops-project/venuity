package com.venuity.server.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LegacyTransactionResponse(
        Long id,
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("customer_name") String customerName,
        String type,
        String amount,
        String notes,
        @JsonProperty("created_at") String createdAt
) {
}
