package com.venuity.server.security;

import com.venuity.common.ErrorCode;
import com.venuity.server.api.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Authentication required")
                    .withLegacyError("Authentication credentials were not provided.");
        }
        return principal;
    }
}
