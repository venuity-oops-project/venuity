package com.venuity.client.api;

import com.venuity.common.ErrorCode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final int statusCode;
    private final Map<String, Object> details;

    public ApiException(ErrorCode code, int statusCode, String message) {
        this(code, statusCode, message, Map.of(), null);
    }

    public ApiException(ErrorCode code, int statusCode, String message, Map<String, Object> details,
                        Throwable cause) {
        super(message == null || message.isBlank() ? code.name() : message, cause);
        this.code = code == null ? ErrorCode.INTERNAL_ERROR : code;
        this.statusCode = statusCode;
        this.details = details == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public ErrorCode code() {
        return code;
    }

    public int statusCode() {
        return statusCode;
    }

    public Map<String, Object> details() {
        return details;
    }

    public boolean isNetworkError() {
        return statusCode == 0;
    }

    public boolean isUnauthorized() {
        return code == ErrorCode.UNAUTHORIZED;
    }
}
