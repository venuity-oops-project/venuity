package com.venuity.client.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.venuity.common.ErrorCode;
import com.venuity.client.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_GET_RETRIES = 2;
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final HttpClient http;
    private final Consumer<Duration> sleeper;
    private final AtomicReference<Session> session = new AtomicReference<>();
    private final AtomicBoolean unauthorizedNotified = new AtomicBoolean();
    private volatile URI baseUri;
    private volatile Runnable unauthorizedHandler = () -> {
    };

    public ApiClient(String serverUrl) {
        this(serverUrl, HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build(), ApiClient::sleep);
    }

    ApiClient(String serverUrl, HttpClient http, Consumer<Duration> sleeper) {
        this.baseUri = URI.create(AppConfig.normalizeServerUrl(serverUrl));
        this.http = http;
        this.sleeper = sleeper;
    }

    public void setBaseUri(String serverUrl) {
        baseUri = URI.create(AppConfig.normalizeServerUrl(serverUrl));
    }

    public String baseUrl() {
        return baseUri.toString();
    }

    public Session session() {
        return session.get();
    }

    public void setUnauthorizedHandler(Runnable handler) {
        unauthorizedHandler = handler == null ? () -> {
        } : handler;
    }

    public Session login(String username, String password) {
        LoginPayload payload = request(HttpMethod.POST, "/api/auth/login",
                new LoginRequest(username, password), LoginPayload.class, null, false, false);
        Session created = new Session(payload.accessToken(), payload.refreshToken(),
                payload.expiresAt(), payload.user());
        session.set(created);
        unauthorizedNotified.set(false);
        return created;
    }

    public User me() {
        return get("/api/auth/me", User.class);
    }

    public Settings settings() {
        return get("/api/settings", Settings.class);
    }

    public Health ping() {
        return request(HttpMethod.GET, "/api/health", null, Health.class, null, false, true);
    }

    public void logout() {
        Session current = session.getAndSet(null);
        unauthorizedNotified.set(false);
        if (current == null) {
            return;
        }
        try {
            request(HttpMethod.POST, "/api/auth/logout", new RefreshRequest(current.refreshToken()),
                    Void.class, null, false, false);
        } catch (ApiException exception) {
            log.debug("Server logout did not complete", exception);
        }
    }

    public <T> T get(String path, Class<T> type) {
        return request(HttpMethod.GET, path, null, type, null, true, true);
    }

    public <T> T get(String path, TypeReference<T> type) {
        return request(HttpMethod.GET, path, null, type, null, true, true);
    }

    public <T> T post(String path, Object body, Class<T> type) {
        return post(path, body, type, null);
    }

    public <T> T post(String path, Object body, Class<T> type, String idempotencyKey) {
        return request(HttpMethod.POST, path, body, type, idempotencyKey, true, false);
    }

    public <T> T put(String path, Object body, Class<T> type) {
        return request(HttpMethod.PUT, path, body, type, null, true, false);
    }

    public void delete(String path) {
        request(HttpMethod.DELETE, path, null, Void.class, null, true, false);
    }

    public byte[] getBytes(String path) {
        return requestBytes(path);
    }

    public static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    private <T> T request(HttpMethod method, String path, Object body, Class<T> type,
                          String idempotencyKey, boolean authenticated, boolean retry) {
        byte[] payload = requestBytes(method, path, body, idempotencyKey, authenticated, retry);
        if (type == Void.class) {
            return null;
        }
        if (payload == null || payload.length == 0) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, 200, "The server returned an empty response");
        }
        try {
            return MAPPER.readValue(payload, type);
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, 200,
                    "Unable to read the server response", Map.of(), exception);
        }
    }

    private <T> T request(HttpMethod method, String path, Object body, TypeReference<T> type,
                          String idempotencyKey, boolean authenticated, boolean retry) {
        byte[] payload = requestBytes(method, path, body, idempotencyKey, authenticated, retry);
        if (payload == null || payload.length == 0) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, 200, "The server returned an empty response");
        }
        try {
            return MAPPER.readValue(payload, type);
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, 200,
                    "Unable to read the server response", Map.of(), exception);
        }
    }

    private byte[] requestBytes(String path) {
        return requestBytes(HttpMethod.GET, path, null, null, true, true);
    }

    private byte[] requestBytes(HttpMethod method, String path, Object body, String idempotencyKey,
                                boolean authenticated, boolean retry) {
        HttpRequest request = buildRequest(method, path, body, idempotencyKey, authenticated);
        HttpResponse<byte[]> response = send(request, retry);
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return response.body();
        }
        ErrorPayload error = parseError(response.body());
        if (status == 401 && authenticated) {
            handleUnauthorized();
        }
        throw toException(status, error);
    }

    private HttpRequest buildRequest(HttpMethod method, String path, Object body,
                                     String idempotencyKey, boolean authenticated) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("API path must start with '/'");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUri + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
        if (authenticated) {
            Session current = session.get();
            if (current == null) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, 401, "Sign in required");
            }
            builder.header("Authorization", "Bearer " + current.accessToken());
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        if (body == null) {
            builder.method(method.name(), HttpRequest.BodyPublishers.noBody());
        } else {
            try {
                String json = MAPPER.writeValueAsString(body);
                builder.header("Content-Type", "application/json")
                        .method(method.name(), HttpRequest.BodyPublishers.ofString(json));
            } catch (JsonProcessingException exception) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, 0,
                        "Unable to prepare the request", Map.of(), exception);
            }
        }
        return builder.build();
    }

    private HttpResponse<byte[]> send(HttpRequest request, boolean retry) {
        int attempt = 0;
        while (true) {
            try {
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (retry && response.statusCode() >= 500 && attempt < MAX_GET_RETRIES) {
                    attempt++;
                    sleeper.accept(backoff(attempt));
                    continue;
                }
                return response;
            } catch (IOException exception) {
                if (retry && attempt < MAX_GET_RETRIES) {
                    attempt++;
                    sleeper.accept(backoff(attempt));
                    continue;
                }
                throw new ApiException(ErrorCode.INTERNAL_ERROR, 0,
                        "Cannot reach the server. Check that it is running and the URL is correct.",
                        Map.of(), exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ApiException(ErrorCode.INTERNAL_ERROR, 0, "The request was interrupted",
                        Map.of(), exception);
            }
        }
    }

    private void handleUnauthorized() {
        if (session.getAndSet(null) == null) {
            return;
        }
        if (unauthorizedNotified.compareAndSet(false, true)) {
            try {
                unauthorizedHandler.run();
            } catch (RuntimeException exception) {
                log.warn("Unauthorized handler failed", exception);
            }
        }
    }

    private static ErrorPayload parseError(byte[] body) {
        if (body == null || body.length == 0) {
            return new ErrorPayload(null, null, Map.of());
        }
        try {
            return MAPPER.readValue(body, ErrorPayload.class);
        } catch (IOException exception) {
            return new ErrorPayload(null, null, Map.of());
        }
    }

    private static ApiException toException(int status, ErrorPayload error) {
        ErrorCode code = parseCode(error.code(), status);
        String message = error.message();
        if (message == null || message.isBlank()) {
            message = "Request failed with status " + status;
        }
        return new ApiException(code, status, message, error.details(), null);
    }

    private static ErrorCode parseCode(String value, int status) {
        if (value != null) {
            try {
                return ErrorCode.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return fallbackCode(status);
            }
        }
        return fallbackCode(status);
    }

    private static ErrorCode fallbackCode(int status) {
        return switch (status) {
            case 400 -> ErrorCode.VALIDATION_FAILED;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }

    private static Duration backoff(int attempt) {
        return Duration.ofMillis(200L * attempt);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.INTERNAL_ERROR, 0, "The request was interrupted",
                    Map.of(), exception);
        }
    }

    enum HttpMethod {
        GET,
        POST,
        PUT,
        DELETE
    }

    record LoginRequest(String username, String password) {
    }

    record RefreshRequest(String refresh) {
    }

    record LoginPayload(String accessToken, String refreshToken, Instant expiresAt, User user) {
    }

    record ErrorPayload(String code, String message, Map<String, Object> details) {
    }
}
