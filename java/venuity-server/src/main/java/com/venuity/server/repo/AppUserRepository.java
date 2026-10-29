package com.venuity.server.repo;

import com.venuity.server.domain.AppUser;
import com.venuity.server.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByPublicId(String publicId);

    boolean existsByRole(Role role);
}
