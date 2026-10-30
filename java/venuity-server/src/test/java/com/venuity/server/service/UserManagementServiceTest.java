package com.venuity.server.service;

import com.venuity.common.ErrorCode;
import com.venuity.server.api.dto.BulkActionResult;
import com.venuity.server.api.dto.BulkUserActionRequest;
import com.venuity.server.api.dto.ChangePasswordRequest;
import com.venuity.server.api.dto.ProfileUpdateRequest;
import com.venuity.server.api.dto.ResetPasswordRequest;
import com.venuity.server.api.dto.UserCreateRequest;
import com.venuity.server.api.dto.UserCreateResponse;
import com.venuity.server.api.dto.UserDetailResponse;
import com.venuity.server.api.dto.UserImportPreviewResponse;
import com.venuity.server.api.dto.UserImportResultResponse;
import com.venuity.server.api.dto.UserResponse;
import com.venuity.server.api.dto.UserUpdateRequest;
import com.venuity.server.api.error.ApiException;
import com.venuity.server.domain.AppUser;
import com.venuity.server.domain.AuditLog;
import com.venuity.server.domain.Role;
import com.venuity.server.repo.AppUserRepository;
import com.venuity.server.repo.AuditLogRepository;
import com.venuity.server.repo.UserSessionRepository;
import com.venuity.server.security.AuthPrincipal;
import com.venuity.server.security.BootstrapAdminRunner;
import com.venuity.server.security.PasswordPolicyService;
import com.venuity.server.security.PasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class UserManagementServiceTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("venuity")
            .withUsername("venuity")
            .withPassword("venuity-test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("venuity.jwt.secret", () -> "very-strong-test-secret-key-32-bytes-minimum");
        registry.add("venuity.bootstrap.admin-username", () -> "admin");
        registry.add("venuity.bootstrap.admin-password", () -> "admin");
    }

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private AppUserRepository userRepo;

    @Autowired
    private AuditLogRepository auditLogRepo;

    @Autowired
    private UserSessionRepository sessionRepo;

    @Autowired
    private PasswordPolicyService passwordPolicy;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private AuditService auditService;

    private AuthPrincipal adminPrincipal;
    private AppUser adminUser;

    @BeforeEach
    void setUp() {
        // Ensure at least one active admin
        adminUser = userRepo.findByUsernameIgnoreCase("admin").orElseGet(() -> {
            AppUser u = AppUser.create("admin", passwordService.encode("AdminPass#123"), Role.ADMIN);
            u.setFullName("System Admin");
            return userRepo.save(u);
        });
        adminUser.setEnabled(true);
        adminUser.setDeletedAt(null);
        adminUser.setLockedUntil(null);
        userRepo.save(adminUser);

        adminPrincipal = new AuthPrincipal(adminUser.getId(), adminUser.getPublicId(), adminUser.getUsername(), Role.ADMIN);
    }

    @Test
    @DisplayName("Password policy validates length, complexity, and disallows username and common passwords")
    void testPasswordPolicy() {
        assertThrows(ApiException.class, () -> passwordPolicy.validatePassword("short", "user1"));
        assertThrows(ApiException.class, () -> passwordPolicy.validatePassword("nocapital123", "nocapital123")); // username match
        assertThrows(ApiException.class, () -> passwordPolicy.validatePassword("onlyletters", "user1")); // no digit
        assertThrows(ApiException.class, () -> passwordPolicy.validatePassword("12345678", "user1")); // common
        assertThrows(ApiException.class, () -> passwordPolicy.validatePassword("password", "user1")); // common

        // Valid password
        passwordPolicy.validatePassword("ValidPass#2026", "user1");

        // Temporary password generation
        String temp = passwordPolicy.generateTemporaryPassword();
        assertNotNull(temp);
        assertTrue(temp.length() >= 12);
        assertTrue(temp.chars().anyMatch(Character::isLetter));
        assertTrue(temp.chars().anyMatch(Character::isDigit));
    }

    @Test
    @DisplayName("Create user with manual and generated password, and check username/email conflicts")
    void testCreateUserAndConflicts() {
        String uname = "tester_" + System.currentTimeMillis();
        UserCreateResponse res = userService.create(new UserCreateRequest(
                uname, "StrongPass#1", Role.STAFF, "Tester User", uname + "@playarea.com",
                "1234567890", "Test notes", null, false, true, "Tester", "User"
        ), adminPrincipal, "127.0.0.1");

        assertNotNull(res.user());
        assertEquals(uname, res.user().username());
        assertEquals("STAFF", res.user().role());
        assertTrue(res.user().mustChangePassword());

        // Conflict check: duplicate username
        ApiException userEx = assertThrows(ApiException.class, () -> userService.create(new UserCreateRequest(
                uname, "StrongPass#1", Role.STAFF, "Duplicate", "dup@playarea.com", null, null, null, false, false, null, null
        ), adminPrincipal, "127.0.0.1"));
        assertEquals(ErrorCode.CONFLICT, userEx.getCode());

        // Conflict check: duplicate email
        ApiException emailEx = assertThrows(ApiException.class, () -> userService.create(new UserCreateRequest(
                "other_" + System.currentTimeMillis(), "StrongPass#1", Role.STAFF, "Duplicate Email", uname + "@playarea.com", null, null, null, false, false, null, null
        ), adminPrincipal, "127.0.0.1"));
        assertEquals(ErrorCode.CONFLICT, emailEx.getCode());

        // Generated temporary password
        String autoUname = "auto_" + System.currentTimeMillis();
        UserCreateResponse autoRes = userService.create(new UserCreateRequest(
                autoUname, null, Role.STAFF, null, null, null, null, null, true, true, null, null
        ), adminPrincipal, "127.0.0.1");
        assertNotNull(autoRes.temporaryPassword());
        assertTrue(autoRes.user().mustChangePassword());
    }

    @Test
    @DisplayName("Last admin safety rules prevent disabling, demoting, or archiving the only active admin")
    void testLastAdminProtection() {
        // Ensure only adminUser is active ADMIN
        for (AppUser u : userRepo.findAll()) {
            if (u.getRole() == Role.ADMIN && !u.getId().equals(adminUser.getId())) {
                u.setEnabled(false);
                userRepo.save(u);
            }
        }

        // Attempt to disable last admin
        ApiException disableEx = assertThrows(ApiException.class, () -> userService.disable(adminUser.getId(), new AuthPrincipal(999, "other", "other", Role.ADMIN), "127.0.0.1"));
        assertEquals(ErrorCode.LAST_ADMIN, disableEx.getCode());

        // Attempt to demote last admin
        ApiException demoteEx = assertThrows(ApiException.class, () -> userService.update(adminUser.getId(),
                new UserUpdateRequest(null, null, null, Role.STAFF, null, null),
                new AuthPrincipal(999, "other", "other", Role.ADMIN), "127.0.0.1"));
        assertEquals(ErrorCode.LAST_ADMIN, demoteEx.getCode());

        // Attempt to archive last admin
        ApiException archiveEx = assertThrows(ApiException.class, () -> userService.archive(adminUser.getId(), new AuthPrincipal(999, "other", "other", Role.ADMIN), "127.0.0.1"));
        assertEquals(ErrorCode.LAST_ADMIN, archiveEx.getCode());

        // Self-protection: admin cannot disable or archive self even if another admin exists
        AppUser secondAdmin = userRepo.save(AppUser.create("admin2_" + System.currentTimeMillis(), passwordService.encode("Pass#1234"), Role.ADMIN));
        ApiException selfDisable = assertThrows(ApiException.class, () -> userService.disable(adminUser.getId(), adminPrincipal, "127.0.0.1"));
        assertEquals(ErrorCode.FORBIDDEN, selfDisable.getCode());

        ApiException selfArchive = assertThrows(ApiException.class, () -> userService.archive(adminUser.getId(), adminPrincipal, "127.0.0.1"));
        assertEquals(ErrorCode.FORBIDDEN, selfArchive.getCode());

        // Clean up
        userRepo.delete(secondAdmin);
    }

    @Test
    @DisplayName("Password change and reset bumps token_version and invalidates active sessions")
    void testTokenInvalidationAndSessionRevocation() {
        String uname = "tokentest_" + System.currentTimeMillis();
        UserCreateResponse res = userService.create(new UserCreateRequest(
                uname, "OldPass#1234", Role.STAFF, null, null, null, null, null, false, false, null, null
        ), adminPrincipal, "127.0.0.1");
        Long userId = res.user().id();

        AppUser u = userRepo.findById(userId).orElseThrow();
        int initialVersion = u.getTokenVersion();

        // Perform password reset
        String tempPass = userService.resetPassword(userId, new ResetPasswordRequest(null, true), adminPrincipal, "127.0.0.1");
        assertNotNull(tempPass);

        AppUser afterReset = userRepo.findById(userId).orElseThrow();
        assertEquals(initialVersion + 1, afterReset.getTokenVersion());
        assertTrue(afterReset.isMustChangePassword());

        // Perform user change password
        userService.changePassword(userId, new ChangePasswordRequest(tempPass, "NewStrongPass#2026"), "127.0.0.1");
        AppUser afterChange = userRepo.findById(userId).orElseThrow();
        assertEquals(initialVersion + 2, afterChange.getTokenVersion());
        assertFalse(afterChange.isMustChangePassword());
    }

    @Test
    @DisplayName("Account lockout after 5 failed login attempts and admin unlock")
    void testAccountLockoutAndUnlock() {
        String uname = "locktest_" + System.currentTimeMillis();
        userService.create(new UserCreateRequest(
                uname, "CorrectPass#1234", Role.STAFF, null, null, null, null, null, false, false, null, null
        ), adminPrincipal, "127.0.0.1");

        // Attempt 5 bad logins
        for (int i = 0; i < 5; i++) {
            assertThrows(ApiException.class, () -> authService.login(uname, "WrongPass#999", "192.168.1.1", "TestAgent"));
        }

        AppUser lockedUser = userRepo.findByUsernameIgnoreCase(uname).orElseThrow();
        assertTrue(lockedUser.isLocked(), "Account should be locked after 5 failed attempts");

        // 6th attempt should fail with ACCOUNT_LOCKED
        ApiException lockedEx = assertThrows(ApiException.class, () -> authService.login(uname, "CorrectPass#1234", "192.168.1.1", "TestAgent"));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, lockedEx.getCode());

        // Admin unlocks
        userService.unlock(lockedUser.getId(), adminPrincipal, "127.0.0.1");
        AppUser unlockedUser = userRepo.findByUsernameIgnoreCase(uname).orElseThrow();
        assertFalse(unlockedUser.isLocked());

        // Login now succeeds
        AuthService.LoginResult loginResult = authService.login(uname, "CorrectPass#1234", "192.168.1.1", "TestAgent");
        assertNotNull(loginResult.tokens());
    }

    @Test
    @DisplayName("BootstrapAdminRunner does NOT overwrite credentials when FORCE_RESET=false, and DOES when true")
    void testBootstrapAdminRunnerForceReset() {
        String bootstrapUser = "bootstraptest_" + System.currentTimeMillis();
        String initialPass = "InitialPass#1234";
        String changedPass = "ChangedByAdmin#5678";
        String runnerPass = "RunnerPass#9999";

        // 1. Initial creation
        BootstrapAdminRunner runnerCreate = new BootstrapAdminRunner(userRepo, passwordService, auditService, bootstrapUser, initialPass, false);
        runnerCreate.run(null);

        AppUser u = userRepo.findByUsernameIgnoreCase(bootstrapUser).orElseThrow();
        assertTrue(passwordService.matches(initialPass, u.getPasswordHash()));

        // 2. User changes password via UI
        u.setPasswordHash(passwordService.encode(changedPass));
        userRepo.save(u);

        // 3. Restart app with FORCE_RESET = false -> should NOT overwrite!
        BootstrapAdminRunner runnerNoReset = new BootstrapAdminRunner(userRepo, passwordService, auditService, bootstrapUser, runnerPass, false);
        runnerNoReset.run(null);

        AppUser u2 = userRepo.findByUsernameIgnoreCase(bootstrapUser).orElseThrow();
        assertTrue(passwordService.matches(changedPass, u2.getPasswordHash()), "Password must NOT be overwritten when forceReset is false");
        assertFalse(passwordService.matches(runnerPass, u2.getPasswordHash()));

        // 4. Restart app with FORCE_RESET = true -> MUST overwrite!
        BootstrapAdminRunner runnerWithReset = new BootstrapAdminRunner(userRepo, passwordService, auditService, bootstrapUser, runnerPass, true);
        runnerWithReset.run(null);

        AppUser u3 = userRepo.findByUsernameIgnoreCase(bootstrapUser).orElseThrow();
        assertTrue(passwordService.matches(runnerPass, u3.getPasswordHash()), "Password MUST be overwritten when forceReset is true");
    }

    @Test
    @DisplayName("Bulk operations process multiple users and report partial failures gracefully")
    void testBulkOperations() {
        String u1 = "bulk1_" + System.currentTimeMillis();
        String u2 = "bulk2_" + System.currentTimeMillis();
        UserCreateResponse r1 = userService.create(new UserCreateRequest(u1, "Pass#12345", Role.STAFF, null, null, null, null, null, false, false, null, null), adminPrincipal, "127.0.0.1");
        UserCreateResponse r2 = userService.create(new UserCreateRequest(u2, "Pass#12345", Role.STAFF, null, null, null, null, null, false, false, null, null), adminPrincipal, "127.0.0.1");

        BulkActionResult bulkRes = userService.bulkAction(new BulkUserActionRequest(
                List.of(r1.user().id(), r2.user().id(), 999999L), // 2 valid, 1 invalid
                BulkUserActionRequest.Action.DISABLE,
                null
        ), adminPrincipal, "127.0.0.1");

        assertEquals(3, bulkRes.total());
        assertEquals(2, bulkRes.succeeded());
        assertEquals(1, bulkRes.failed());

        assertFalse(userRepo.findById(r1.user().id()).orElseThrow().isEnabled());
        assertFalse(userRepo.findById(r2.user().id()).orElseThrow().isEnabled());
    }

    @Test
    @DisplayName("CSV Export does not contain password hashes or secrets, and CSV Import works with preview")
    void testCsvExportAndImport() {
        String csvExport = userService.exportCsv(null, null, null);
        assertNotNull(csvExport);
        assertTrue(csvExport.contains("Username"));
        assertFalse(csvExport.contains("password_hash"));
        assertFalse(csvExport.contains("$2a$")); // no bcrypt hash fragments

        // CSV Import preview & commit
        String csvData = "username,full_name,role,email,phone\n" +
                "csv_valid_" + System.currentTimeMillis() + ",CSV Valid User,STAFF,csvvalid@test.com,123456\n" +
                "bad@user,Invalid Username,STAFF,bad@test.com,123456\n";

        ByteArrayInputStream bais = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        UserImportPreviewResponse preview = userService.previewImportCsv(bais);
        assertEquals(2, preview.totalRows());
        assertEquals(1, preview.validRows());
        assertEquals(1, preview.invalidRows());

        ByteArrayInputStream baisCommit = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        UserImportResultResponse result = userService.commitImportCsv(baisCommit, adminPrincipal, "127.0.0.1");
        assertEquals(1, result.totalImported());
        assertNotNull(result.createdUsers().get(0).temporaryPassword());
    }

    @Test
    @DisplayName("Audit entries are created for security events without storing passwords")
    void testAuditEntries() {
        long beforeCount = auditLogRepo.count();
        String uname = "audit_" + System.currentTimeMillis();
        userService.create(new UserCreateRequest(uname, "StrongPass#1", Role.STAFF, null, null, null, null, null, false, false, null, null), adminPrincipal, "127.0.0.1");

        List<AuditLog> entries = auditLogRepo.findAll();
        assertTrue(entries.size() > beforeCount);
        for (AuditLog entry : entries) {
            if (entry.getDetails() != null) {
                assertFalse(entry.getDetails().contains("StrongPass#1"), "Audit details must never leak plain passwords");
                assertFalse(entry.getDetails().contains("$2a$"), "Audit details must never leak password hashes");
            }
        }
    }
}
