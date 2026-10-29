package com.venuity.server.security;

import com.venuity.server.domain.Role;
import com.venuity.server.repo.AppUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionSafetyValidatorTest {

    private static final String STRONG_SECRET = "a-very-strong-production-secret-with-plenty-of-entropy-12345";
    private static final String STRONG_PASSWORD = "SuperSecurePassword#2026!";

    @Test
    @DisplayName("Fails if JWT secret is null or blank")
    void failsIfJwtSecretMissingOrBlank() {
        IllegalStateException exNull = assertThrows(IllegalStateException.class, () ->
                ProductionSafetyValidator.validateJwtSecret(null));
        assertTrue(exNull.getMessage().contains("JWT_SECRET"));

        IllegalStateException exBlank = assertThrows(IllegalStateException.class, () ->
                ProductionSafetyValidator.validateJwtSecret("   "));
        assertTrue(exBlank.getMessage().contains("JWT_SECRET"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "dev-only-venuity-secret-change-me-32-bytes",
            "replace-with-at-least-32-random-bytes"
    })
    @DisplayName("Fails if JWT secret is set to known dev defaults")
    void failsIfJwtSecretIsKnownDefault(String insecureSecret) {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ProductionSafetyValidator.validateJwtSecret(insecureSecret));
        assertTrue(ex.getMessage().contains("known development/placeholder default"));
    }

    @Test
    @DisplayName("Fails if JWT secret is shorter than 32 bytes")
    void failsIfJwtSecretTooShort() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ProductionSafetyValidator.validateJwtSecret("short-secret-less-than-32-b"));
        assertTrue(ex.getMessage().contains("at least 32 bytes"));
    }

    @Test
    @DisplayName("Passes with a secure 32+ byte JWT secret")
    void passesWithValidJwtSecret() {
        assertDoesNotThrow(() -> ProductionSafetyValidator.validateJwtSecret(STRONG_SECRET));
    }

    @Test
    @DisplayName("Fails if no admin exists and bootstrap password is empty")
    void failsIfNoAdminAndBootstrapPasswordEmpty() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ProductionSafetyValidator.validateBootstrapPassword("admin", "", false));
        assertTrue(ex.getMessage().contains("BOOTSTRAP_ADMIN_PASSWORD is required"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "Admin", "ADMIN"})
    @DisplayName("Fails if bootstrap password is 'admin'")
    void failsIfBootstrapPasswordIsAdmin(String adminPass) {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ProductionSafetyValidator.validateBootstrapPassword("admin", adminPass, false));
        assertTrue(ex.getMessage().contains("cannot be 'admin'"));
    }

    @Test
    @DisplayName("Fails if bootstrap password is shorter than 12 characters")
    void failsIfBootstrapPasswordTooShort() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ProductionSafetyValidator.validateBootstrapPassword("admin", "Short#123", false));
        assertTrue(ex.getMessage().contains("at least 12 characters long"));
    }

    @Test
    @DisplayName("Passes if bootstrap password is valid when no admin exists")
    void passesIfBootstrapPasswordValidWhenNoAdmin() {
        assertDoesNotThrow(() ->
                ProductionSafetyValidator.validateBootstrapPassword("admin", STRONG_PASSWORD, false));
    }

    @Test
    @DisplayName("Passes when admin already exists and no bootstrap credentials requested")
    void passesWhenAdminExistsAndBootstrapNotRequested() {
        assertDoesNotThrow(() ->
                ProductionSafetyValidator.validateBootstrapPassword("", "", true));
        assertDoesNotThrow(() ->
                ProductionSafetyValidator.validateBootstrapPassword(null, null, true));
    }

    @Test
    @DisplayName("Fails if admin exists but user requests bootstrap with insecure password")
    void failsIfAdminExistsButWeakBootstrapRequested() {
        assertThrows(IllegalStateException.class, () ->
                ProductionSafetyValidator.validateBootstrapPassword("admin", "admin", true));
        assertThrows(IllegalStateException.class, () ->
                ProductionSafetyValidator.validateBootstrapPassword("admin", "short", true));
    }

    @Test
    @DisplayName("Full validate() passes with valid configuration")
    void fullValidatePasses() {
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.existsByRole(Role.ADMIN)).thenReturn(true);

        ProductionSafetyValidator validator = new ProductionSafetyValidator(
                users, STRONG_SECRET, "", "");

        assertDoesNotThrow(validator::validate);
    }

    @Test
    @DisplayName("Full validate() fails when JWT secret is invalid")
    void fullValidateFailsOnBadSecret() {
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.existsByRole(Role.ADMIN)).thenReturn(true);

        ProductionSafetyValidator validator = new ProductionSafetyValidator(
                users, "dev-only-venuity-secret-change-me-32-bytes", "", "");

        assertThrows(IllegalStateException.class, validator::validate);
    }
}
