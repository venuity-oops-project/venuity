package com.venuity.server.api.dto;

import java.math.BigDecimal;

public record HourlyAnalyticsPoint(int hour, long visits, BigDecimal revenue) {
}
