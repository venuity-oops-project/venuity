package com.venuity.server.security;

import com.venuity.server.domain.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JwtService {

    public record JwtPair(String access, String refresh, Instant accessExpiresAt, Instant refreshExpiresAt) {
    }

    private final SecretKey key;
    private final long accessMinutes;
    private final long refreshDays;
    private final Map<String, Instant> blacklistedRefreshTokens = new ConcurrentHashMap<>();

    public JwtService(@Value("${venuity.jwt.secret}") String secret,
                      @Value("${venuity.jwt.access-minutes:480}") long accessMinutes,
                      @Value("${venuity.jwt.refresh-days:7}") long refreshDays) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessMinutes = accessMinutes;
        this.refreshDays = refreshDays;
    }

    public JwtPair issue(AppUser user) {
        Instant now = Instant.now();
        Instant accessExpiry = now.plus(accessMinutes, ChronoUnit.MINUTES);
        Instant refreshExpiry = now.plus(refreshDays, ChronoUnit.DAYS);
        String access = token(user, "access", now, accessExpiry);
        String refresh = token(user, "refresh", now, refreshExpiry);
        return new JwtPair(access, refresh, accessExpiry, refreshExpiry);
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isBlacklisted(String jti) {
        Instant expiry = blacklistedRefreshTokens.get(jti);
        if (expiry == null) {
            return false;
        }
        if (expiry.isBefore(Instant.now())) {
            blacklistedRefreshTokens.remove(jti);
            return false;
        }
        return true;
    }

    public void blacklist(String jti, Instant expiry) {
        blacklistedRefreshTokens.put(jti, expiry);
    }

    private String token(AppUser user, String type, Instant issuedAt, Instant expiry) {
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .id(UUID.randomUUID().toString())
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .claim("type", type)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }
}
