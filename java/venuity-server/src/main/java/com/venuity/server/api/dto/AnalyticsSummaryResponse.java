package com.venuity.server.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AnalyticsSummaryResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal totalRevenue,
        long checkIns,
        long totalCustomers,
        double averageCheckInsPerDay
) {
}
