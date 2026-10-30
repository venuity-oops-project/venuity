package com.venuity.server.service;

import com.venuity.server.domain.AppUser;
import com.venuity.server.domain.UserSession;
import com.venuity.server.repo.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class UserSessionService {

    private final UserSessionRepository sessionRepo;

    public UserSessionService(UserSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    public static String hashToken(String token) {
        if (token == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }

    @Transactional
    public UserSession createSession(AppUser user, String refreshToken, Instant expiresAt, String ip, String userAgent) {
        String hash = hashToken(refreshToken);
        UserSession session = UserSession.create(user, hash, expiresAt, ip, userAgent);
        return sessionRepo.save(session);
    }

    @Transactional(readOnly = true)
    public Optional<UserSession> findValidSession(String refreshToken) {
        String hash = hashToken(refreshToken);
        return sessionRepo.findByRefreshTokenHash(hash)
                .filter(UserSession::isActive);
    }

    @Transactional
    public void touchSession(UserSession session) {
        session.setLastSeenAt(Instant.now());
        sessionRepo.save(session);
    }

    @Transactional
    public void revokeSession(String refreshToken) {
        String hash = hashToken(refreshToken);
        sessionRepo.findByRefreshTokenHash(hash).ifPresent(session -> {
            session.setRevokedAt(Instant.now());
            sessionRepo.save(session);
        });
    }

    @Transactional
    public boolean revokeSessionById(Long userId, Long sessionId) {
        Optional<UserSession> opt = sessionRepo.findById(sessionId);
        if (opt.isPresent()) {
            UserSession session = opt.get();
            if (session.getUser().getId().equals(userId)) {
                session.setRevokedAt(Instant.now());
                sessionRepo.save(session);
                return true;
            }
        }
        return false;
    }

    @Transactional
    public int revokeAllSessions(Long userId) {
        return sessionRepo.revokeAllForUser(userId, Instant.now());
    }

    @Transactional
    public int revokeOtherSessions(Long userId, Long currentSessionId) {
        return sessionRepo.revokeOthersForUser(userId, currentSessionId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<UserSession> getActiveSessions(Long userId) {
        return sessionRepo.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId);
    }

    @Transactional
    public int cleanupExpiredSessions(int retentionDays) {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        return sessionRepo.deleteExpiredOrRevokedOlderThan(cutoff);
    }
}
