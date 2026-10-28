package com.venuity.server.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerResponse(
        String publicId,
        String publicCode,
        String name,
        String phone,
        BigDecimal walletBalance,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
