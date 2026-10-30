package com.venuity.server.service;

import com.venuity.common.ErrorCode;
import com.venuity.server.api.dto.AuditLogResponse;
import com.venuity.server.api.dto.BulkActionResult;
import com.venuity.server.api.dto.BulkUserActionRequest;
import com.venuity.server.api.dto.ChangePasswordRequest;
import com.venuity.server.api.dto.ProfileUpdateRequest;
import com.venuity.server.api.dto.ResetPasswordRequest;
import com.venuity.server.api.dto.TransactionResponse;
import com.venuity.server.api.dto.UserActivityResponse;
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
import com.venuity.server.domain.TxType;
import com.venuity.server.domain.WalletTransaction;
import com.venuity.server.repo.AppUserRepository;
import com.venuity.server.repo.AuditLogRepository;
import com.venuity.server.repo.WalletTransactionRepository;
import com.venuity.server.security.AuthPrincipal;
import com.venuity.server.security.PasswordPolicyService;
import com.venuity.server.security.PasswordService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class UserService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,32}$");

    private final AppUserRepository users;
    private final PasswordService passwords;
    private final PasswordPolicyService passwordPolicy;
    private final AuthService authService;
    private final UserSessionService sessionService;
    private final AuditService auditService;
    private final AuditLogRepository auditLogRepo;
    private final WalletTransactionRepository transactionRepo;

    public UserService(AppUserRepository users, PasswordService passwords,
                       PasswordPolicyService passwordPolicy, AuthService authService,
                       UserSessionService sessionService, AuditService auditService,
                       AuditLogRepository auditLogRepo,
                       WalletTransactionRepository transactionRepo) {
        this.users = users;
        this.passwords = passwords;
        this.passwordPolicy = passwordPolicy;
        this.authService = authService;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.auditLogRepo = auditLogRepo;
        this.transactionRepo = transactionRepo;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(String query, Role role, String status, Pageable pageable) {
        Specification<AppUser> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query != null && !query.isBlank()) {
                String search = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                Predicate matchUsername = cb.like(cb.lower(root.get("username")), search);
                Predicate matchFullName = cb.like(cb.lower(root.get("fullName")), search);
                Predicate matchEmail = cb.like(cb.lower(root.get("email")), search);
                Predicate matchPhone = cb.like(cb.lower(root.get("phone")), search);
                predicates.add(cb.or(matchUsername, matchFullName, matchEmail, matchPhone));
            }

            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
                String upper = status.trim().toUpperCase(Locale.ROOT);
                switch (upper) {
                    case "ACTIVE" -> {
                        predicates.add(cb.isTrue(root.get("enabled")));
                        predicates.add(cb.isNull(root.get("deletedAt")));
                        predicates.add(cb.or(cb.isNull(root.get("lockedUntil")), cb.lessThanOrEqualTo(root.get("lockedUntil"), Instant.now())));
                        predicates.add(cb.or(cb.isNull(root.get("accountExpiresAt")), cb.greaterThan(root.get("accountExpiresAt"), Instant.now())));
                    }
                    case "DISABLED" -> {
                        predicates.add(cb.isFalse(root.get("enabled")));
                        predicates.add(cb.isNull(root.get("deletedAt")));
                    }
                    case "LOCKED" -> {
                        predicates.add(cb.isNotNull(root.get("lockedUntil")));
                        predicates.add(cb.greaterThan(root.get("lockedUntil"), Instant.now()));
                        predicates.add(cb.isNull(root.get("deletedAt")));
                    }
                    case "EXPIRED" -> {
                        predicates.add(cb.isNotNull(root.get("accountExpiresAt")));
                        predicates.add(cb.lessThanOrEqualTo(root.get("accountExpiresAt"), Instant.now()));
                        predicates.add(cb.isNull(root.get("deletedAt")));
                    }
                    case "ARCHIVED" -> predicates.add(cb.isNotNull(root.get("deletedAt")));
                    default -> {
                    }
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return users.findAll(spec, pageable).map(authService::modernUser);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll().stream().map(authService::modernUser).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return authService.modernUser(requireUser(id));
    }

    @Transactional(readOnly = true)
    public UserDetailResponse getUserDetail(Long id) {
        AppUser user = requireUser(id);
        UserActivityResponse activity = getActivity(id);
        int activeSessionsCount = sessionService.getActiveSessions(id).size();
        List<AuditLogResponse> logs = auditLogRepo.findTop20ByTargetUserIdOrderByOccurredAtDesc(id).stream()
                .map(this::toAuditResponse)
                .toList();

        return new UserDetailResponse(authService.modernUser(user), activity, activeSessionsCount, logs);
    }

    @Transactional(readOnly = true)
    public UserActivityResponse getActivity(Long userId) {
        AppUser user = requireUser(userId);
        long rechargesCount = transactionRepo.countByUserIdAndType(userId, TxType.TOPUP);
        BigDecimal rechargesTotal = transactionRepo.sumByUserIdAndType(userId, TxType.TOPUP);
        long checkinsCount = transactionRepo.countByUserIdAndType(userId, TxType.CHECKIN);
        BigDecimal checkinsTotal = transactionRepo.sumByUserIdAndType(userId, TxType.CHECKIN);
        List<TransactionResponse> recent = transactionRepo.findTop20ByCreatedByIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toTransactionResponse)
                .toList();

        return new UserActivityResponse(userId, user.getUsername(), rechargesCount, rechargesTotal,
                checkinsCount, checkinsTotal, recent);
    }

    @Transactional
    public UserCreateResponse create(UserCreateRequest request, AuthPrincipal actor, String ip) {
        String username = request.username() != null ? request.username().trim() : "";
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Username must be 3-32 characters and contain only letters, digits, dots, underscores, or hyphens.");
        }

        if (users.findByUsernameIgnoreCase(username).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                    "Username '" + username + "' is already taken.");
        }

        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim();
            if (users.findByEmailIgnoreCase(email).isPresent()) {
                throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                        "Email '" + email + "' is already in use.");
            }
        }

        Role role = request.role() != null ? request.role() : Role.STAFF;
        String temporaryPassword = null;
        String passwordToHash;
        boolean forceChange = Boolean.TRUE.equals(request.mustChangePassword());

        if (Boolean.TRUE.equals(request.generatePassword()) || request.password() == null || request.password().isBlank()) {
            temporaryPassword = passwordPolicy.generateTemporaryPassword();
            passwordToHash = temporaryPassword;
            forceChange = true;
        } else {
            passwordPolicy.validatePassword(request.password(), username);
            passwordToHash = request.password();
        }

        AppUser user = AppUser.create(username, passwords.encode(passwordToHash), role);
        user.setMustChangePassword(forceChange);
        user.setAccountExpiresAt(request.accountExpiresAt());
        user.setNotes(request.notes());
        if (actor != null) {
            user.setCreatedBy(actor.id());
        }

        String fullName = request.fullName() != null && !request.fullName().isBlank()
                ? request.fullName().trim()
                : ((request.firstName() != null ? request.firstName().trim() : "") + " " + (request.lastName() != null ? request.lastName().trim() : "")).trim();
        user.setFullName(fullName);

        if (request.email() != null && !request.email().isBlank()) {
            user.setEmail(request.email().trim());
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            user.setPhone(request.phone().trim());
        }

        users.save(user);

        auditService.record(
                actor != null ? actor.id() : null,
                actor != null ? actor.username() : "SYSTEM",
                "USER_CREATED",
                user.getId(),
                user.getUsername(),
                ip,
                "{\"role\":\"" + role + "\",\"mustChangePassword\":" + forceChange + "}",
                true
        );

        return new UserCreateResponse(authService.modernUser(user), temporaryPassword);
    }

    public UserResponse create(UserCreateRequest request) {
        return create(request, null, "127.0.0.1").user();
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request, AuthPrincipal actor, String ip) {
        AppUser user = requireUser(id);

        if (request.role() != null && request.role() != user.getRole()) {
            if (user.getRole() == Role.ADMIN && request.role() == Role.STAFF) {
                if (actor != null && Objects.equals(actor.id(), id)) {
                    throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                            "You cannot demote your own account.");
                }
                assertNotLastActiveAdmin(id, "demote");
            }
            user.setRole(request.role());
            user.bumpTokenVersion();
            sessionService.revokeAllSessions(id);
            auditService.record(actor != null ? actor.id() : null, actor != null ? actor.username() : "SYSTEM",
                    "ROLE_CHANGED", id, user.getUsername(), ip, "{\"newRole\":\"" + request.role() + "\"}", true);
        }

        if (request.email() != null && !request.email().isBlank()) {
            String newEmail = request.email().trim();
            users.findByEmailIgnoreCase(newEmail).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                            "Email '" + newEmail + "' is already in use by another account.");
                }
            });
            user.setEmail(newEmail);
        } else if (request.email() != null && request.email().isBlank()) {
            user.setEmail(null);
        }

        if (request.fullName() != null) user.setFullName(request.fullName().trim());
        if (request.phone() != null) user.setPhone(request.phone().trim());
        if (request.notes() != null) user.setNotes(request.notes().trim());
        if (request.accountExpiresAt() != null) user.setAccountExpiresAt(request.accountExpiresAt());

        users.save(user);
        auditService.record(actor != null ? actor.id() : null, actor != null ? actor.username() : "SYSTEM",
                "USER_UPDATED", id, user.getUsername(), ip, null, true);

        return authService.modernUser(user);
    }

    @Transactional
    public UserResponse disable(Long id, AuthPrincipal actor, String ip) {
        AppUser user = requireUser(id);
        if (actor != null && Objects.equals(actor.id(), id)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "You cannot disable your own account.");
        }
        if (user.getRole() == Role.ADMIN) {
            assertNotLastActiveAdmin(id, "disable");
        }

        user.setEnabled(false);
        user.bumpTokenVersion();
        sessionService.revokeAllSessions(id);
        users.save(user);

        auditService.record(actor != null ? actor.id() : null, actor != null ? actor.username() : "SYSTEM",
                "USER_DISABLED", id, user.getUsername(), ip, null, true);
        return authService.modernUser(user);
    }

    @Transactional
    public UserResponse enable(Long id, AuthPrincipal actor, String ip) {
        AppUser user = requireUser(id);
        user.setEnabled(true);
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        users.save(user);

        auditService.record(actor != null ? actor.id() : null, actor != null ? actor.username() : "SYSTEM",
                "USER_ENABLED", id, user.getUsername(), ip, null, true);
        return authService.modernUser(user);
    }

    @Transactional
    public UserResponse unlock(Long id, AuthPrincipal actor, String ip) {
        AppUser user = requireUser(id);
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        users.save(user);

        auditService.record(actor != null ? actor.id() : null, actor != null ? actor.username() : "SYSTEM",
                "USER_UNLOCKED", id, user.getUsername(), ip, null, true);
        return authService.modernUser(user);
    }

    @Transactional
    public String resetPassword(Long id, ResetPasswordRequest request, AuthPrincipal actor, String ip) {
        AppUser user = requireUser(id);
        String tempPassword;
        if (request == null || Boolean.TRUE.equals(request.generatePassword()) || request.password() == null || request.password().isBlank()) {
            tempPassword = passwordPolicy.generateTemporaryPassword();
        } else {
            passwordPolicy.validatePassword(request.password(), user.getUsername());
            tempPassword = request.password();
        }

        user.setPasswordHash(passwords.encode(tempPassword));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(Instant.now());
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        user.bumpTokenVersion();
        sessionService.revokeAllSessions(id);
        users.save(user);

        auditService.record(actor != null ? actor.id() : null, actor != null ? actor.username() : "SYSTEM",
                "PASSWORD_RESET", id, user.getUsername(), ip, "{\"forcedChange\":true}", true);

        return tempPassword;
    }

    @Transactional
    public void forceLogout(Long id, AuthPrincipal actor, String ip) {
        AppUser user = requireUser(id);
        user.bumpTokenVersion();
        sessionService.revokeAllSessions(id);
        users.save(user);

        auditService.record(actor != null ? actor.id() : null, actor != null ? actor.username() : "SYSTEM",
                "FORCE_LOGOUT", id, user.getUsername(), ip, null, true);
    }

    @Transactional
    public void archive(Long id, AuthPrincipal actor, String ip) {
        AppUser user = requireUser(id);
        if (actor != null && Objects.equals(actor.id(), id)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "You cannot archive your own account.");
        }
        if (user.getRole() == Role.ADMIN) {
            assertNotLastActiveAdmin(id, "archive");
        }

        user.setDeletedAt(Instant.now());
        user.setEnabled(false);
        user.bumpTokenVersion();
        sessionService.revokeAllSessions(id);
        users.save(user);

        auditService.record(actor != null ? actor.id() : null, actor != null ? actor.username() : "SYSTEM",
                "USER_ARCHIVED", id, user.getUsername(), ip, null, true);
    }

    @Transactional
    public UserResponse restore(Long id, AuthPrincipal actor, String ip) {
        AppUser user = requireUser(id);
        user.setDeletedAt(null);
        user.setEnabled(true);
        users.save(user);

        auditService.record(actor != null ? actor.id() : null, actor != null ? actor.username() : "SYSTEM",
                "USER_RESTORED", id, user.getUsername(), ip, null, true);
        return authService.modernUser(user);
    }

    @Transactional
    public BulkActionResult bulkAction(BulkUserActionRequest request, AuthPrincipal actor, String ip) {
        List<BulkActionResult.RowResult> results = new ArrayList<>();
        int succeeded = 0;
        int failed = 0;

        for (Long userId : request.userIds()) {
            try {
                switch (request.action()) {
                    case ENABLE -> enable(userId, actor, ip);
                    case DISABLE -> disable(userId, actor, ip);
                    case ARCHIVE -> archive(userId, actor, ip);
                    case RESTORE -> restore(userId, actor, ip);
                    case SET_ROLE -> {
                        if (request.role() == null) {
                            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Role is required");
                        }
                        update(userId, new UserUpdateRequest(null, null, null, request.role(), null, null), actor, ip);
                    }
                }
                results.add(new BulkActionResult.RowResult(userId, true, "Success"));
                succeeded++;
            } catch (Exception e) {
                results.add(new BulkActionResult.RowResult(userId, false, e.getMessage()));
                failed++;
            }
        }

        auditService.record(actor != null ? actor.id() : null, actor != null ? actor.username() : "SYSTEM",
                "BULK_ACTION", null, null, ip,
                "{\"action\":\"" + request.action() + "\",\"total\":" + request.userIds().size() + ",\"succeeded\":" + succeeded + ",\"failed\":" + failed + "}",
                failed == 0);

        return new BulkActionResult(request.userIds().size(), succeeded, failed, results);
    }

    @Transactional(readOnly = true)
    public String exportCsv(String query, Role role, String status) {
        Page<UserResponse> page = listUsers(query, role, status, PageRequest.of(0, 10000, Sort.by("id").ascending()));
        StringWriter writer = new StringWriter();
        writer.write("ID,Username,Full Name,Role,Email,Phone,Status,Enabled,Failed Logins,Created Date,Last Login\n");

        for (UserResponse u : page.getContent()) {
            writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%d\",\"%s\",\"%s\"\n",
                    u.id(),
                    escapeCsv(u.username()),
                    escapeCsv(u.fullName()),
                    u.role(),
                    escapeCsv(u.email()),
                    escapeCsv(u.phone()),
                    u.status(),
                    u.enabled(),
                    u.failedLoginAttempts(),
                    u.dateJoined() != null ? u.dateJoined().toString() : "",
                    u.lastLogin() != null ? u.lastLogin().toString() : ""
            ));
        }
        return writer.toString();
    }

    public UserImportPreviewResponse previewImportCsv(InputStream is) {
        List<UserImportPreviewResponse.PreviewRow> rows = new ArrayList<>();
        int validCount = 0;
        int invalidCount = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            boolean headerSkipped = false;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isBlank()) continue;
                if (!headerSkipped && (line.toLowerCase().contains("username") || line.toLowerCase().contains("email"))) {
                    headerSkipped = true;
                    continue;
                }

                String[] parts = parseCsvLine(line);
                String username = parts.length > 0 ? parts[0].trim() : "";
                String fullName = parts.length > 1 ? parts[1].trim() : "";
                String roleStr = parts.length > 2 ? parts[2].trim().toUpperCase() : "STAFF";
                String email = parts.length > 3 ? parts[3].trim() : "";
                String phone = parts.length > 4 ? parts[4].trim() : "";

                StringBuilder errors = new StringBuilder();
                if (!USERNAME_PATTERN.matcher(username).matches()) {
                    errors.append("Invalid username (must be 3-32 alphanumeric chars). ");
                } else if (users.findByUsernameIgnoreCase(username).isPresent()) {
                    errors.append("Username already taken. ");
                }

                if (!email.isBlank()) {
                    if (!email.contains("@")) {
                        errors.append("Invalid email format. ");
                    } else if (users.findByEmailIgnoreCase(email).isPresent()) {
                        errors.append("Email already in use. ");
                    }
                }

                if (!"ADMIN".equals(roleStr) && !"STAFF".equals(roleStr)) {
                    errors.append("Invalid role (must be ADMIN or STAFF). ");
                }

                boolean valid = errors.isEmpty();
                if (valid) validCount++; else invalidCount++;
                rows.add(new UserImportPreviewResponse.PreviewRow(
                        lineNumber, username, fullName, email, phone, roleStr, valid, valid ? null : errors.toString().trim()
                ));
            }
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Failed to parse CSV file: " + e.getMessage());
        }

        return new UserImportPreviewResponse(rows.size(), validCount, invalidCount, rows);
    }

    @Transactional
    public UserImportResultResponse commitImportCsv(InputStream is, AuthPrincipal actor, String ip) {
        UserImportPreviewResponse preview = previewImportCsv(is);
        List<UserImportResultResponse.ImportedUserCredential> created = new ArrayList<>();

        for (UserImportPreviewResponse.PreviewRow row : preview.rows()) {
            if (!row.valid()) continue;
            Role role = "ADMIN".equalsIgnoreCase(row.role()) ? Role.ADMIN : Role.STAFF;
            String tempPassword = passwordPolicy.generateTemporaryPassword();

            AppUser user = AppUser.create(row.username(), passwords.encode(tempPassword), role);
            user.setFullName(row.fullName());
            if (row.email() != null && !row.email().isBlank()) user.setEmail(row.email());
            if (row.phone() != null && !row.phone().isBlank()) user.setPhone(row.phone());
            user.setMustChangePassword(true);
            if (actor != null) user.setCreatedBy(actor.id());

            users.save(user);
            created.add(new UserImportResultResponse.ImportedUserCredential(
                    user.getUsername(), user.getFullName(), user.getRole().name(), tempPassword
            ));
        }

        auditService.record(actor != null ? actor.id() : null, actor != null ? actor.username() : "SYSTEM",
                "USERS_IMPORTED", null, null, ip,
                "{\"importedCount\":" + created.size() + "}", true);

        return new UserImportResultResponse(created.size(), created);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request, String ip) {
        AppUser user = requireUser(userId);
        if (!passwords.matches(request.currentPassword(), user.getPasswordHash())) {
            auditService.record(userId, user.getUsername(), "PASSWORD_CHANGE_FAILED",
                    userId, user.getUsername(), ip, "Bad current password", false);
            throw new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }

        passwordPolicy.validatePassword(request.newPassword(), user.getUsername());
        user.setPasswordHash(passwords.encode(request.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        user.setMustChangePassword(false);
        user.bumpTokenVersion();
        sessionService.revokeAllSessions(userId);
        users.save(user);

        auditService.record(userId, user.getUsername(), "PASSWORD_CHANGED",
                userId, user.getUsername(), ip, null, true);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, ProfileUpdateRequest request, String ip) {
        AppUser user = requireUser(userId);
        if (request.email() != null && !request.email().isBlank()) {
            String newEmail = request.email().trim();
            users.findByEmailIgnoreCase(newEmail).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) {
                    throw new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT,
                            "Email '" + newEmail + "' is already in use by another account.");
                }
            });
            user.setEmail(newEmail);
        } else if (request.email() != null && request.email().isBlank()) {
            user.setEmail(null);
        }

        if (request.fullName() != null) user.setFullName(request.fullName().trim());
        if (request.phone() != null) user.setPhone(request.phone().trim());

        users.save(user);
        auditService.record(userId, user.getUsername(), "PROFILE_UPDATED",
                userId, user.getUsername(), ip, null, true);

        return authService.modernUser(user);
    }

    private void assertNotLastActiveAdmin(Long targetUserId, String actionName) {
        long activeAdmins = users.countActiveByRole(Role.ADMIN);
        if (activeAdmins <= 1) {
            throw new ApiException(ErrorCode.LAST_ADMIN, HttpStatus.BAD_REQUEST,
                    "Cannot " + actionName + " the last active administrator.");
        }
    }

    private AppUser requireUser(Long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND,
                "User #" + id + " not found."));
    }

    private AuditLogResponse toAuditResponse(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getOccurredAt(), a.getActorUserId(),
                a.getActorUsername(), a.getAction(), a.getTargetUserId(),
                a.getTargetUsername(), a.getIp(), a.getDetails(), a.isSuccess());
    }

    private TransactionResponse toTransactionResponse(WalletTransaction t) {
        return new TransactionResponse(t.getId(), t.getCustomer().getPublicId(),
                t.getCustomer().getName(), t.getType().name(), t.getAmount(),
                t.getBalanceAfter(), t.getNote(), t.getCreatedAt());
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        return val.replace("\"", "\"\"");
    }

    private String[] parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }
}
