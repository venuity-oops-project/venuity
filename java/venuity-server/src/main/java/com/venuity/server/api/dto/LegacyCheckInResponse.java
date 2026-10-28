package com.venuity.server.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LegacyCheckInResponse(
        boolean success,
        LegacyCustomerResponse customer,
        @JsonProperty("fee_charged") String feeCharged
) {
}
