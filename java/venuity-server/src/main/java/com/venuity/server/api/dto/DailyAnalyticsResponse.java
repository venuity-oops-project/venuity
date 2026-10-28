package com.venuity.server.api.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyAnalyticsResponse(
        LocalDate from,
        LocalDate to,
        List<DailyAnalyticsPoint> points
) {
}
