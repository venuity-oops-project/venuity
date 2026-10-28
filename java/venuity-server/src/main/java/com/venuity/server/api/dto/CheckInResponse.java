package com.venuity.server.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CheckInResponse(
        String customerName,
        BigDecimal feeCharged,
        BigDecimal balanceAfter,
        Instant checkedInAt,
        CustomerResponse customer
) {
}
