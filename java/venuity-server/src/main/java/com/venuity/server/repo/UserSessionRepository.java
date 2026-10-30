package com.venuity.server.repo;

import com.venuity.server.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    List<UserSession> findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(Long userId);

    List<UserSession> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.user.id = :userId AND s.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.user.id = :userId AND s.id <> :currentSessionId AND s.revokedAt IS NULL")
    int revokeOthersForUser(@Param("userId") Long userId, @Param("currentSessionId") Long currentSessionId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :cutoff OR (s.revokedAt IS NOT NULL AND s.revokedAt < :cutoff)")
    int deleteExpiredOrRevokedOlderThan(@Param("cutoff") Instant cutoff);
}
