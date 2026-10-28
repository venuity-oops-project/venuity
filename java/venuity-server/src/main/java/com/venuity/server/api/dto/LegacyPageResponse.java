package com.venuity.server.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record LegacyPageResponse<T>(
        long count,
        @JsonProperty("total_pages") int totalPages,
        String next,
        String previous,
        List<T> results
) {
}
