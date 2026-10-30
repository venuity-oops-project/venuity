package com.venuity.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "public_id", nullable = false, length = 36)
    private String publicId;

    @Column(nullable = false, length = 150)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 254)
    private String email;

    @Column(name = "first_name", nullable = false, length = 150)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 150)
    private String lastName;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "last_login")
    private Instant lastLogin;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "account_expires_at")
    private Instant accountExpiresAt;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "date_joined", insertable = false, updatable = false)
    private Instant dateJoined;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    public static AppUser create(String username, String passwordHash, Role role) {
        AppUser user = new AppUser();
        user.publicId = UUID.randomUUID().toString();
        user.username = username;
        user.passwordHash = passwordHash;
        user.role = role;
        user.enabled = true;
        user.email = null;
        user.firstName = "";
        user.lastName = "";
        user.fullName = "";
        user.phone = null;
        user.notes = null;
        user.mustChangePassword = false;
        user.failedLoginAttempts = 0;
        user.tokenVersion = 0;
        return user;
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFullName() {
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        String combined = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
        return !combined.isBlank() ? combined : username;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName != null ? fullName : "";
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(Instant passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }

    public Instant getLastLogin() {
        return lastLoginAt != null ? lastLoginAt : lastLogin;
    }

    public void setLastLogin(Instant lastLogin) {
        this.lastLogin = lastLogin;
        this.lastLoginAt = lastLogin;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt != null ? lastLoginAt : lastLogin;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
        this.lastLogin = lastLoginAt;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void setLastLoginIp(String lastLoginIp) {
        this.lastLoginIp = lastLoginIp;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Instant getAccountExpiresAt() {
        return accountExpiresAt;
    }

    public void setAccountExpiresAt(Instant accountExpiresAt) {
        this.accountExpiresAt = accountExpiresAt;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(int tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public void bumpTokenVersion() {
        this.tokenVersion++;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getDateJoined() {
        return dateJoined;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isArchived() {
        return deletedAt != null;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public boolean isExpired() {
        return accountExpiresAt != null && accountExpiresAt.isBefore(Instant.now());
    }

    public String computeStatus() {
        if (isArchived()) return "ARCHIVED";
        if (!enabled) return "DISABLED";
        if (isLocked()) return "LOCKED";
        if (isExpired()) return "EXPIRED";
        return "ACTIVE";
    }
}
