package com.venuity.server.api.dto;

public record LegacyLoginResponse(String access, String refresh, LegacyUserResponse user) {
}
