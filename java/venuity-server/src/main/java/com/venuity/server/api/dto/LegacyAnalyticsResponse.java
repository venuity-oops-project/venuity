package com.venuity.server.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record LegacyAnalyticsResponse(
        List<String> labels,
        List<Number> data,
        @JsonProperty("display_date") String displayDate,
        String interval,
        String metric
) {
}
