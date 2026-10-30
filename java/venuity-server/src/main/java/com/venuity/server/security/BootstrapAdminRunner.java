package com.venuity.server.security;

import com.venuity.server.domain.AppUser;
import com.venuity.server.domain.Role;
import com.venuity.server.repo.AppUserRepository;
import com.venuity.server.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(100)
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final AppUserRepository users;
    private final PasswordService passwords;
    private final AuditService auditService;
    private final String username;
    private final String password;
    private final boolean forceReset;

    public BootstrapAdminRunner(AppUserRepository users, PasswordService passwords,
                                AuditService auditService,
                                @Value("${venuity.bootstrap.admin-username:}") String username,
                                @Value("${venuity.bootstrap.admin-password:}") String password,
                                @Value("${venuity.bootstrap.force-reset:false}") boolean forceReset) {
        this.users = users;
        this.passwords = passwords;
        this.auditService = auditService;
        this.username = username;
        this.password = password;
        this.forceReset = forceReset;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank()) {
            log.info("[BootstrapAdmin] No bootstrap admin credentials specified; skipping admin bootstrap.");
            return;
        }
        var existing = users.findByUsernameIgnoreCase(username);
        if (existing.isPresent()) {
            AppUser user = existing.get();
            if (!forceReset) {
                log.info("[BootstrapAdmin] Admin user '{}' already exists and BOOTSTRAP_ADMIN_FORCE_RESET is false. Keeping existing credentials.", username);
                return;
            }
            log.warn("[BootstrapAdmin] BOOTSTRAP_ADMIN_FORCE_RESET is true. Overwriting credentials and role for existing admin '{}'.", username);
            user.setPasswordHash(passwords.encode(password));
            user.setEnabled(true);
            user.setRole(Role.ADMIN);
            user.setLockedUntil(null);
            user.setAccountExpiresAt(null);
            user.setDeletedAt(null);
            user.setFailedLoginAttempts(0);
            user.bumpTokenVersion();
            users.save(user);
            auditService.record(null, "BOOTSTRAP", "BOOTSTRAP_RESET", user.getId(), user.getUsername(),
                    "127.0.0.1", "{\"reason\":\"BOOTSTRAP_ADMIN_FORCE_RESET triggered\"}", true);
            log.info("[BootstrapAdmin] Reset credentials for existing admin user '{}'.", username);
            return;
        }

        AppUser newUser = AppUser.create(username, passwords.encode(password), Role.ADMIN);
        newUser.setFullName("Administrator");
        users.save(newUser);
        auditService.record(null, "BOOTSTRAP", "BOOTSTRAP_CREATE", newUser.getId(), newUser.getUsername(),
                "127.0.0.1", "{\"reason\":\"Initial bootstrap admin created\"}", true);
        log.info("[BootstrapAdmin] Successfully created bootstrap admin user '{}'.", username);
    }
}
