package com.venuity.server.security;

import com.venuity.server.domain.Role;
import com.venuity.server.repo.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Validates critical security settings upon startup when running under the 'prod' profile.
 * Fails fast with informative exceptions if development secrets or weak credentials are detected.
 */
@Component
@Profile("prod")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionSafetyValidator implements ApplicationRunner {

    public static final Set<String> INSECURE_JWT_SECRETS = Set.of(
            "dev-only-venuity-secret-change-me-32-bytes",
            "replace-with-at-least-32-random-bytes"
    );

    public static final int MIN_JWT_SECRET_BYTES = 32;
    public static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

    private final AppUserRepository users;
    private final String jwtSecret;
    private final String bootstrapUsername;
    private final String bootstrapPassword;

    public ProductionSafetyValidator(AppUserRepository users,
                                     @Value("${venuity.jwt.secret:}") String jwtSecret,
                                     @Value("${venuity.bootstrap.admin-username:}") String bootstrapUsername,
                                     @Value("${venuity.bootstrap.admin-password:}") String bootstrapPassword) {
        this.users = users;
        this.jwtSecret = jwtSecret;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    public void validate() {
        validateJwtSecret(jwtSecret);
        boolean adminExists = users.existsByRole(Role.ADMIN);
        validateBootstrapPassword(bootstrapUsername, bootstrapPassword, adminExists);
    }

    public static void validateJwtSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Production safety check failed: JWT_SECRET environment variable is missing or blank. "
                            + "Generate a secure 256-bit secret using: openssl rand -base64 48");
        }
        String trimmed = secret.trim();
        if (INSECURE_JWT_SECRETS.contains(trimmed)) {
            throw new IllegalStateException(
                    "Production safety check failed: JWT_SECRET matches a known development/placeholder default. "
                            + "Please supply a strong, randomly generated secret.");
        }
        if (trimmed.getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException(
                    "Production safety check failed: JWT_SECRET must be at least " + MIN_JWT_SECRET_BYTES
                            + " bytes long for secure HS256 signing (detected "
                            + trimmed.getBytes(StandardCharsets.UTF_8).length + " bytes).");
        }
    }

    public static void validateBootstrapPassword(String username, String password, boolean adminExists) {
        boolean bootstrapRequested = (username != null && !username.isBlank())
                || (password != null && !password.isBlank());

        if (!bootstrapRequested && adminExists) {
            return;
        }

        if (password == null || password.isBlank()) {
            String reason = !adminExists
                    ? "no ADMIN user exists in the database"
                    : "bootstrap admin creation/sync was requested";
            throw new IllegalStateException(
                    "Production safety check failed: BOOTSTRAP_ADMIN_PASSWORD is required because " + reason + ".");
        }

        if ("admin".equalsIgnoreCase(password.trim())) {
            throw new IllegalStateException(
                    "Production safety check failed: BOOTSTRAP_ADMIN_PASSWORD cannot be 'admin' in production.");
        }

        if (password.length() < MIN_ADMIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "Production safety check failed: BOOTSTRAP_ADMIN_PASSWORD must be at least "
                            + MIN_ADMIN_PASSWORD_LENGTH + " characters long (detected "
                            + password.length() + " characters).");
        }
    }
}
