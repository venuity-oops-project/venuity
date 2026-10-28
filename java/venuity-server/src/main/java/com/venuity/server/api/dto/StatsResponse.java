package com.venuity.server.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record StatsResponse(
        long totalCustomers,
        BigDecimal totalRevenue,
        List<TransactionResponse> recentTransactions
) {
}
