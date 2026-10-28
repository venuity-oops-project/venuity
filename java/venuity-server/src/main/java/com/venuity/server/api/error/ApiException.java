package com.venuity.server.api.error;

import com.venuity.common.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;
    private HttpStatus modernStatus;
    private final Map<String, Object> details;
    private String legacyError;
    private String legacyCodeOverride;
    private Object legacyDetail;
    private boolean legacyIncludeCode = true;

    public ApiException(ErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = new LinkedHashMap<>();
    }

    public ApiException withDetail(String key, Object value) {
        details.put(key, value);
        return this;
    }

    public ApiException withLegacyError(String error) {
        this.legacyError = error;
        return this;
    }

    public ApiException withLegacyDetail(Object detail) {
        this.legacyDetail = detail;
        return this;
    }

    public ApiException withLegacyCode(String code) {
        this.legacyCodeOverride = code;
        return this;
    }

    public ApiException withoutLegacyCode() {
        this.legacyIncludeCode = false;
        return this;
    }

    public ApiException withModernStatus(HttpStatus status) {
        this.modernStatus = status;
        return this;
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public HttpStatus getModernStatus() {
        return modernStatus == null ? status : modernStatus;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public String getLegacyError() {
        return legacyError;
    }

    public String getLegacyCodeOverride() {
        return legacyCodeOverride;
    }

    public Object getLegacyDetail() {
        return legacyDetail;
    }

    public boolean isLegacyIncludeCode() {
        return legacyIncludeCode;
    }
}
