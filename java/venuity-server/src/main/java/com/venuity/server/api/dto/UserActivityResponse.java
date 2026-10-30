package com.venuity.server.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record UserActivityResponse(
        Long userId,
        String username,
        long rechargesCount,
        BigDecimal rechargesTotal,
        long checkinsCount,
        BigDecimal checkinsTotal,
        List<TransactionResponse> recentTransactions
) {
}
