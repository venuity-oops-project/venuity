package com.venuity.server.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record LegacyStatsResponse(
        @JsonProperty("total_customers") long totalCustomers,
        @JsonProperty("total_revenue") BigDecimal totalRevenue,
        @JsonProperty("recent_transactions") List<LegacyTransactionResponse> recentTransactions
) {
}
