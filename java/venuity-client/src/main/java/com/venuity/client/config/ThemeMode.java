package com.venuity.client.config;

public enum ThemeMode {
    DARK,
    LIGHT;

    public static ThemeMode parse(String value) {
        if (value != null && "light".equalsIgnoreCase(value.trim())) {
            return LIGHT;
        }
        return DARK;
    }

    public String preferenceValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
