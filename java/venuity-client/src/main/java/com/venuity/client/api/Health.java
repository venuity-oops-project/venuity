package com.venuity.client.api;

public record Health(String status, String version) {

    public boolean isUp() {
        return "UP".equalsIgnoreCase(status);
    }
}
