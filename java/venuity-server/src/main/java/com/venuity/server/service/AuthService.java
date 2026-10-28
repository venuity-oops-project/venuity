package com.venuity.server.service;

import com.venuity.common.ErrorCode;
import com.venuity.server.api.dto.LegacyUserResponse;
import com.venuity.server.api.dto.UserResponse;
import com.venuity.server.api.error.ApiException;
import com.venuity.server.domain.AppUser;
import com.venuity.server.domain.Role;
import com.venuity.server.repo.AppUserRepository;
import com.venuity.server.security.JwtService;
import com.venuity.server.security.PasswordService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    public record LoginResult(AppUser user, JwtService.JwtPair tokens) {
    }

    private static final int MAX_FAILURES = 5;

    private final AppUserRepository users;
    private final PasswordService passwords;
    private final JwtService jwtService;
    private final SettingsService settings;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public AuthService(AppUserRepository users, PasswordService passwords, JwtService jwtService,
                       SettingsService settings) {
        this.users = users;
        this.passwords = passwords;
        this.jwtService = jwtService;
        this.settings = settings;
    }

    @Transactional
    public LoginResult login(String username, String password, String remoteAddress) {
        String rateKey = remoteAddress + "|" + username;
        checkRateLimit(rateKey);
        AppUser user = users.findByUsername(username).orElse(null);
        if (user == null || !user.isEnabled() || !passwords.matches(password, user.getPasswordHash())) {
            recordFailure(rateKey);
            throw badCredentials();
        }
        failures.remove(rateKey);
        if (passwords.needsRehash(user.getPasswordHash())) {
            user.setPasswordHash(passwords.encode(password));
        }
        user.setLastLogin(Instant.now());
        users.save(user);
        return new LoginResult(user, jwtService.issue(user));
    }

    @Transactional
    public JwtService.JwtPair refresh(String refreshToken) {
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
        long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException exception) {
            throw badRefresh();
        }
        AppUser user = users.findById(userId)
                .filter(AppUser::isEnabled)
                .orElseThrow(this::badRefresh);
        jwtService.blacklist(claims.getId(), claims.getExpiration().toInstant());
        return jwtService.issue(user);
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
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED,
                        "Authentication required"));
    }

    public UserResponse modernUser(AppUser user) {
        return new UserResponse(user.getPublicId(), user.getUsername(), user.getRole().name(),
                user.getEmail(), user.getFirstName(), user.getLastName(), user.getLastLogin(),
                user.getDateJoined());
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
            if (attempts.size() >= MAX_FAILURES) {
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
