package com.venuity.server.api.error;

import com.venuity.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ErrorResponseFactory {

    public boolean isLegacy(String path) {
        return path != null && path.startsWith("/api/v1/");
    }

    public Map<String, Object> modern(ApiException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", exception.getCode().name());
        body.put("message", exception.getMessage());
        body.put("timestamp", Instant.now().toString());
        body.put("details", exception.getDetails());
        return body;
    }

    public Map<String, Object> legacy(ApiException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", legacyMessage(exception));
        if (exception.isLegacyIncludeCode()) {
            String code = exception.getLegacyCodeOverride() != null
                    ? exception.getLegacyCodeOverride()
                    : legacyCode(exception.getCode());
            if (code != null) {
                body.put("code", code);
            }
        }
        if (exception.getLegacyDetail() != null) {
            body.put("detail", exception.getLegacyDetail());
        }
        return body;
    }

    public ApiException unauthenticated() {
        return new ApiException(ErrorCode.UNAUTHORIZED, org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Authentication required")
                .withLegacyError("Authentication credentials were not provided.");
    }

    public ApiException forbidden() {
        return new ApiException(ErrorCode.FORBIDDEN, org.springframework.http.HttpStatus.FORBIDDEN,
                "Admin access required")
                .withLegacyError("You do not have permission to perform this action. Admin access required.");
    }

    private String legacyMessage(ApiException exception) {
        if (exception.getLegacyError() != null) {
            return exception.getLegacyError();
        }
        return switch (exception.getCode()) {
            case VALIDATION_FAILED -> "Validation failed.";
            case UNAUTHORIZED -> "Authentication credentials were not provided.";
            case FORBIDDEN -> "You do not have permission to perform this action. Admin access required.";
            case NOT_FOUND -> "No Customer matches the given query.";
            case CUSTOMER_INACTIVE -> exception.getMessage();
            case INVALID_QR -> exception.getMessage();
            case INSUFFICIENT_BALANCE -> exception.getMessage();
            case AMOUNT_OUT_OF_RANGE -> "Validation failed.";
            case IDEMPOTENCY_CONFLICT, CONFLICT, LAST_ADMIN, ACCOUNT_LOCKED, PASSWORD_POLICY, PASSWORD_CHANGE_REQUIRED -> exception.getMessage();
            case INTERNAL_ERROR -> "An internal server error occurred.";
        };
    }

    private String legacyCode(ErrorCode code) {
        return switch (code) {
            case VALIDATION_FAILED, AMOUNT_OUT_OF_RANGE -> "validation_error";
            case UNAUTHORIZED -> "not_authenticated";
            case FORBIDDEN -> "permission_denied";
            case NOT_FOUND -> "not_found";
            case INTERNAL_ERROR -> "server_error";
            default -> null;
        };
    }
}
