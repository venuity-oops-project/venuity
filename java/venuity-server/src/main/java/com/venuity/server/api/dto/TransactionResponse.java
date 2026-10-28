package com.venuity.server.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        String customerPublicId,
        String customerName,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String note,
        Instant createdAt
) {
}
