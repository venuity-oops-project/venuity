package com.venuity.server.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyAnalyticsPoint(LocalDate date, long visits, BigDecimal revenue) {
}
