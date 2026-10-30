package com.venuity.server.api.dto;

import com.venuity.server.domain.Role;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BulkUserActionRequest(
        @NotEmpty List<Long> userIds,
        @NotNull Action action,
        Role role
) {
    public enum Action {
        ENABLE,
        DISABLE,
        ARCHIVE,
        RESTORE,
        SET_ROLE
    }
}
