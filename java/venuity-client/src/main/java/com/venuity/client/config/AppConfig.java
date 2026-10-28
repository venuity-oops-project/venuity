package com.venuity.client.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class AppConfig {

    public static final String DEFAULT_SERVER_URL = "http://localhost:8080";

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static final String NODE = "com/venuity/client";
    private static final String SERVER_URL = "server.url";
    private static final String THEME = "ui.theme";
    private static final String CAMERA_INDEX = "camera.index";

    private final Preferences preferences;
    private String serverUrl;
    private ThemeMode theme;
    private int cameraIndex;

    AppConfig(Preferences preferences) {
        this.preferences = preferences;
        this.serverUrl = initialServerUrl(preferences.get(SERVER_URL, DEFAULT_SERVER_URL));
        this.theme = ThemeMode.parse(preferences.get(THEME, ThemeMode.DARK.preferenceValue()));
        this.cameraIndex = parseCameraIndex(preferences.get(CAMERA_INDEX, "0"));
    }

    public static AppConfig load() {
        return new AppConfig(Preferences.userRoot().node(NODE));
    }

    static AppConfig load(Preferences preferences) {
        return new AppConfig(preferences);
    }

    public String serverUrl() {
        return serverUrl;
    }

    public void setServerUrl(String value) {
        String normalized = normalizeServerUrl(value);
        serverUrl = normalized;
        preferences.put(SERVER_URL, normalized);
        flush();
    }

    public ThemeMode theme() {
        return theme;
    }

    public void setTheme(ThemeMode value) {
        if (value == null) {
            return;
        }
        theme = value;
        preferences.put(THEME, value.preferenceValue());
        flush();
    }

    public int cameraIndex() {
        return cameraIndex;
    }

    public void setCameraIndex(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Camera index must be zero or greater");
        }
        cameraIndex = value;
        preferences.putInt(CAMERA_INDEX, value);
        flush();
    }

    public static String normalizeServerUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Server URL is required");
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri;
        try {
            uri = new URI(normalized);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Enter a valid server URL", exception);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Enter a valid http(s) server URL");
        }
        return normalized;
    }

    private static String initialServerUrl(String value) {
        try {
            return normalizeServerUrl(value);
        } catch (IllegalArgumentException exception) {
            log.warn("Ignoring invalid saved server URL; using {}", DEFAULT_SERVER_URL);
            return DEFAULT_SERVER_URL;
        }
    }

    private static int parseCameraIndex(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return Math.max(parsed, 0);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void flush() {
        try {
            preferences.flush();
        } catch (BackingStoreException exception) {
            log.warn("Unable to persist client preferences", exception);
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "AppConfig[serverUrl=%s, theme=%s, cameraIndex=%d]",
                serverUrl, theme, cameraIndex);
    }
}
