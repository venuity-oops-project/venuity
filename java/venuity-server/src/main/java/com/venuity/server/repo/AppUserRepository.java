package com.venuity.server.repo;

import com.venuity.server.domain.AppUser;
import com.venuity.server.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    Optional<AppUser> findByPublicId(String publicId);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    @Query("SELECT COUNT(u) FROM AppUser u WHERE u.role = :role AND u.enabled = true AND u.deletedAt IS NULL AND (u.lockedUntil IS NULL OR u.lockedUntil <= CURRENT_TIMESTAMP) AND (u.accountExpiresAt IS NULL OR u.accountExpiresAt > CURRENT_TIMESTAMP)")
    long countActiveByRole(@Param("role") Role role);
}
