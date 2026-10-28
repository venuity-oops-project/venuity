package com.venuity.server.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LegacyUserResponse(
        @JsonProperty("public_id") String publicId,
        String username,
        String email,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("is_staff") boolean staff,
        @JsonProperty("is_superuser") boolean superuser,
        @JsonProperty("last_login") String lastLogin,
        @JsonProperty("date_joined") String dateJoined
) {
}
