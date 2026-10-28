package com.venuity.server.security;

import com.venuity.server.domain.AppUser;
import com.venuity.server.domain.Role;
import com.venuity.server.repo.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordService passwords;
    private final String username;
    private final String password;

    public BootstrapAdminRunner(AppUserRepository users, PasswordService passwords,
                                @Value("${venuity.bootstrap.admin-username:}") String username,
                                @Value("${venuity.bootstrap.admin-password:}") String password) {
        this.users = users;
        this.passwords = passwords;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank()) {
            return;
        }
        var existing = users.findByUsername(username);
        if (existing.isPresent()) {
            AppUser user = existing.get();
            boolean changed = false;
            if (!passwords.matches(password, user.getPasswordHash())) {
                user.setPasswordHash(passwords.encode(password));
                changed = true;
            }
            if (!user.isEnabled()) {
                user.setEnabled(true);
                changed = true;
            }
            if (user.getRole() != Role.ADMIN) {
                user.setRole(Role.ADMIN);
                changed = true;
            }
            if (changed) {
                users.save(user);
            }
            return;
        }
        users.save(AppUser.create(username, passwords.encode(password), Role.ADMIN));
    }
}
