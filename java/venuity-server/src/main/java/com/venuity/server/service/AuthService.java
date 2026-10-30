package com.venuity.server.service;

import com.venuity.common.ErrorCode;
import com.venuity.server.api.dto.LegacyUserResponse;
import com.venuity.server.api.dto.UserResponse;
import com.venuity.server.api.error.ApiException;
import com.venuity.server.domain.AppUser;
import com.venuity.server.domain.Role;
import com.venuity.server.domain.UserSession;
import com.venuity.server.repo.AppUserRepository;
import com.venuity.server.security.JwtService;
import com.venuity.server.security.PasswordService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    public record LoginResult(AppUser user, JwtService.JwtPair tokens) {
    }

    private final AppUserRepository users;
    private final PasswordService passwords;
    private final JwtService jwtService;
    private final SettingsService settings;
    private final UserSessionService sessionService;
    private final AuditService auditService;

    private final int maxFailedAttempts;
    private final int lockoutMinutes;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public AuthService(AppUserRepository users, PasswordService passwords, JwtService jwtService,
                       SettingsService settings, UserSessionService sessionService,
                       AuditService auditService,
                       @Value("${venuity.security.max-failed-logins:5}") int maxFailedAttempts,
                       @Value("${venuity.security.lockout-minutes:15}") int lockoutMinutes) {
        this.users = users;
        this.passwords = passwords;
        this.jwtService = jwtService;
        this.settings = settings;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutMinutes = lockoutMinutes;
    }

    @Transactional(noRollbackFor = {ApiException.class})
    public LoginResult login(String username, String password, String remoteAddress, String userAgent) {
        String rateKey = remoteAddress + "|" + (username != null ? username.toLowerCase() : "");
        checkRateLimit(rateKey);

        AppUser user = users.findByUsernameIgnoreCase(username != null ? username.trim() : "").orElse(null);
        if (user == null || user.isArchived() || !user.isEnabled() || user.isExpired()) {
            recordFailure(rateKey);
            auditService.record(null, username != null ? username : "UNKNOWN", "LOGIN_FAILED",
                    null, null, remoteAddress, "Account not found or inactive", false);
            throw badCredentials();
        }

        if (user.isLocked()) {
            auditService.record(user.getId(), user.getUsername(), "LOGIN_BLOCKED",
                    user.getId(), user.getUsername(), remoteAddress,
                    "Account locked until " + user.getLockedUntil(), false);
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED, HttpStatus.LOCKED,
                    "Account is temporarily locked. Try again later.");
        }

        if (!passwords.matches(password, user.getPasswordHash())) {
            recordFailure(rateKey);
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= maxFailedAttempts) {
                Instant lockUntil = Instant.now().plus(lockoutMinutes, ChronoUnit.MINUTES);
                user.setLockedUntil(lockUntil);
                auditService.record(user.getId(), user.getUsername(), "ACCOUNT_LOCKOUT",
                        user.getId(), user.getUsername(), remoteAddress,
                        "Consecutive failed attempts (" + attempts + ") reached threshold. Locked until " + lockUntil, false);
            } else {
                auditService.record(user.getId(), user.getUsername(), "LOGIN_FAILED",
                        user.getId(), user.getUsername(), remoteAddress,
                        "Bad credentials (attempt " + attempts + " of " + maxFailedAttempts + ")", false);
            }
            users.save(user);
            throw badCredentials();
        }

        failures.remove(rateKey);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        user.setLastLoginIp(remoteAddress);

        if (passwords.needsRehash(user.getPasswordHash())) {
            user.setPasswordHash(passwords.encode(password));
        }
        users.save(user);

        JwtService.JwtPair tokens = jwtService.issue(user);
        sessionService.createSession(user, tokens.refresh(), tokens.refreshExpiresAt(), remoteAddress, userAgent);
        auditService.record(user.getId(), user.getUsername(), "LOGIN_SUCCESS",
                user.getId(), user.getUsername(), remoteAddress, null, true);

        return new LoginResult(user, tokens);
    }

    public LoginResult login(String username, String password, String remoteAddress) {
        return login(username, password, remoteAddress, "");
    }

    @Transactional
    public JwtService.JwtPair refresh(String refreshToken, String remoteAddress, String userAgent) {
        Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw badRefresh();
        }
        if (!"refresh".equals(claims.get("type", String.class))
                || claims.getId() == null
                || jwtService.isBlacklisted(claims.getId())) {
            throw badRefresh();
        }

        UserSession session = sessionService.findValidSession(refreshToken).orElseThrow(this::badRefresh);

        long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException exception) {
            throw badRefresh();
        }
        AppUser user = users.findById(userId)
                .filter(u -> u.isEnabled() && !u.isArchived() && !u.isLocked() && !u.isExpired())
                .orElseThrow(this::badRefresh);

        Integer tokenVer = claims.get("ver", Integer.class);
        if (tokenVer != null && tokenVer != user.getTokenVersion()) {
            sessionService.revokeSession(refreshToken);
            throw badRefresh();
        }

        // Rotate refresh token
        jwtService.blacklist(claims.getId(), claims.getExpiration().toInstant());
        sessionService.revokeSession(refreshToken);

        JwtService.JwtPair newTokens = jwtService.issue(user);
        sessionService.createSession(user, newTokens.refresh(), newTokens.refreshExpiresAt(), remoteAddress, userAgent);

        return newTokens;
    }

    public JwtService.JwtPair refresh(String refreshToken) {
        return refresh(refreshToken, "", "");
    }

    @Transactional
    public void logout(String refreshToken, Long userId, String username, String remoteAddress) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            sessionService.revokeSession(refreshToken);
            blacklist(refreshToken);
        }
        if (userId != null) {
            auditService.record(userId, username, "LOGOUT", userId, username, remoteAddress, null, true);
        }
    }

    public void blacklist(String refreshToken) {
        try {
            Claims claims = jwtService.parse(refreshToken);
            if ("refresh".equals(claims.get("type", String.class)) && claims.getId() != null) {
                jwtService.blacklist(claims.getId(), claims.getExpiration().toInstant());
            }
        } catch (JwtException | IllegalArgumentException ignored) {
        }
    }

    @Transactional(readOnly = true)
    public AppUser requireUser(long userId) {
        return users.findById(userId)
                .filter(u -> u.isEnabled() && !u.isArchived() && !u.isLocked() && !u.isExpired())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED,
                        "Authentication required"));
    }

    public UserResponse modernUser(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getPublicId(),
                user.getUsername(),
                user.getRole().name(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getNotes(),
                user.computeStatus(),
                user.isEnabled(),
                user.isMustChangePassword(),
                user.getFailedLoginAttempts(),
                user.getLockedUntil(),
                user.getAccountExpiresAt(),
                user.getLastLogin(),
                user.getDateJoined(),
                user.getUpdatedAt(),
                user.getCreatedBy()
        );
    }

    public LegacyUserResponse legacyUser(AppUser user) {
        ZoneId zone = settings.zone();
        return new LegacyUserResponse(user.getPublicId(), user.getUsername(), user.getEmail(),
                user.getFirstName(), user.getLastName(),
                user.getRole() == Role.ADMIN || user.getRole() == Role.STAFF,
                user.getRole() == Role.ADMIN,
                TimeUtil.legacy(user.getLastLogin(), zone),
                TimeUtil.legacy(user.getDateJoined(), zone));
    }

    private void checkRateLimit(String key) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return;
        }
        synchronized (attempts) {
            Instant cutoff = Instant.now().minusSeconds(60);
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
                attempts.removeFirst();
            }
            if (attempts.size() >= 10) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.TOO_MANY_REQUESTS,
                        "Too many login attempts. Try again later.")
                        .withLegacyError("Too many login attempts. Try again later.")
                        .withLegacyCode("throttled");
            }
        }
    }

    private void recordFailure(String key) {
        Deque<Instant> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            attempts.addLast(Instant.now());
        }
    }

    private ApiException badCredentials() {
        return new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid username or password")
                .withLegacyError("No active account found with the given credentials")
                .withLegacyCode("no_active_account");
    }

    private ApiException badRefresh() {
        return new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid refresh token")
                .withLegacyError("Token is invalid or expired")
                .withLegacyCode("token_not_valid");
    }
}
