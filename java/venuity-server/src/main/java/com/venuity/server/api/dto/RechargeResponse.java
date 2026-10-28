package com.venuity.server.api.dto;

import java.math.BigDecimal;

public record RechargeResponse(
        CustomerResponse customer,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Long transactionId
) {
}
