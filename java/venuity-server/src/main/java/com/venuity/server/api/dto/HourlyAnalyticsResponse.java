package com.venuity.server.api.dto;

import java.time.LocalDate;
import java.util.List;

public record HourlyAnalyticsResponse(
        LocalDate from,
        LocalDate to,
        List<HourlyAnalyticsPoint> points
) {
}
