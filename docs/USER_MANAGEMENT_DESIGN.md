# User Management System Design & Technical Specification

## Phase 0: Discovery & Architecture Assessment

### 1. Existing Backend State
- **Entity & Table (`app_user`):**
  - Columns: `id` (BIGINT PK), `public_id` (CHAR(36)), `username` (VARCHAR(150) UNIQUE), `password_hash` (VARCHAR(255)), `role` (ENUM('ADMIN','STAFF')), `enabled` (BOOLEAN), `email` (VARCHAR(254)), `first_name` (VARCHAR(150)), `last_name` (VARCHAR(150)), `last_login` (TIMESTAMP(3)), `date_joined` (TIMESTAMP(3)).
  - Repository: `AppUserRepository` (`findByUsername`, `findByPublicId`, `existsByRole`).
  - DTOs: `UserCreateRequest` (username, password, role, email, firstName, lastName), `UserResponse` (publicId, username, role, email, firstName, lastName, lastLogin, dateJoined), `LegacyUserResponse`.
  - Service: `UserService` currently contains only `create` and `list`.
  - Controller: `UserController` with `@PreAuthorize("hasRole('ADMIN')")`, exposing `GET /api/users` and `POST /api/users`.

- **Security & Session Architecture:**
  - `SecurityConfig`: Stateless session creation policy. Public endpoints permit all for `/api/auth/login`, `/api/auth/refresh`, swagger docs, health check, and static frontend assets. All other endpoints require authentication.
  - `JwtService`: Generates HS256 JWT tokens. Claims: `sub` (userId), `id` (jti UUID), `username`, `role`, `type` ("access" or "refresh"). In-memory blacklist map `blacklistedRefreshTokens` (lost upon server restart).
  - `JwtAuthenticationFilter`: Reads `Authorization: Bearer <token>`, parses claims, retrieves `AppUser` by ID from `AppUserRepository`, verifies `enabled`, and populates `SecurityContext` with `AuthPrincipal(id, publicId, username, role)`.
  - `BootstrapAdminRunner`: On startup, if `BOOTSTRAP_ADMIN_USER` and `BOOTSTRAP_ADMIN_PASSWORD` are present, either creates or overwrites the admin password and role.
  - Foreign Key References:
    - `wallet_transaction`: `created_by` references `app_user(id)`.
    - `idempotency_record`: `user_id` references `app_user(id)`.
    - Hard deletion of an `app_user` who has processed wallet transactions is blocked by database foreign key constraints; soft deletion (`deleted_at`) is required.

- **Flyway Migrations:**
  - `V1__init.sql`: Core schema (tables: `app_user`, `business_settings`, `customer`, `wallet_transaction`, `check_in`, `idempotency_record`, view `v_daily_stats`).
  - `V2__seed_settings.sql`: Initial settings row.
  - `V3__add_business_timezone.sql`: Adds `business_timezone` column.
  - Next migration: `V4__user_management.sql`.

### 2. Existing Frontend State
- **Single Page Application:**
  - Pure ES modules in `java/venuity-server/src/main/resources/static/js/`.
  - Router in `app.js`: Hash-based (`#/`, `#/dashboard`, `#/customers`, `#/recharge`, `#/scan`, `#/analytics`, `#/settings`).
  - Shared UI library in `ui.js`: `openModal`, `confirmDialog`, `busyButton`, `debounce`, `emptyState`, `escapeHtml`, `icon`, `toast`.
  - Current Settings page (`settings.js`): Has `#btn-header-add-user` in the header and `#btn-aside-add-user` in the sidebar card, both triggering a modal to create a user via `api.createUser()`.
  - User session in JS: `state.user` in `store.js`, token management in `api.js` (tokens stored in `localStorage`).

---

## Phase 1 & 2: Data Model & Backend Architecture

### 1. Database Schema Extension (`V4__user_management.sql`)

#### A. Extended `app_user` Table
Extend `app_user` with safe default values for existing rows:
- `full_name` VARCHAR(255) NOT NULL DEFAULT ''
- `phone` VARCHAR(30) NULL
- `notes` TEXT NULL
- `must_change_password` BOOLEAN NOT NULL DEFAULT FALSE
- `password_changed_at` TIMESTAMP(3) NULL
- `last_login_at` TIMESTAMP(3) NULL
- `last_login_ip` VARCHAR(45) NULL
- `failed_login_attempts` INT NOT NULL DEFAULT 0
- `locked_until` TIMESTAMP(3) NULL
- `account_expires_at` TIMESTAMP(3) NULL
- `token_version` INT NOT NULL DEFAULT 0
- `deleted_at` TIMESTAMP(3) NULL
- `created_by` BIGINT NULL, CONSTRAINT `fk_user_created_by` FOREIGN KEY (`created_by`) REFERENCES `app_user`(`id`)
- `updated_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
- Unique index on `username` (case-insensitive in utf8mb4 / lower constraint).

#### B. Persistent Session Table (`user_session`)
Replaces the in-memory blacklist so session revocation survives server restarts:
- `id` BIGINT AUTO_INCREMENT PRIMARY KEY
- `user_id` BIGINT NOT NULL
- `refresh_token_hash` VARCHAR(64) NOT NULL
- `created_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
- `last_seen_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
- `expires_at` TIMESTAMP(3) NOT NULL
- `ip` VARCHAR(45) NOT NULL DEFAULT ''
- `user_agent` VARCHAR(512) NOT NULL DEFAULT ''
- `revoked_at` TIMESTAMP(3) NULL
- Foreign key: `CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE`
- Indexes: `idx_session_user (user_id)`, `idx_session_token_hash (refresh_token_hash)`, `idx_session_expires (expires_at)`

#### C. Persistent Audit Log Table (`audit_log`)
Append-only log for compliance and security traceability:
- `id` BIGINT AUTO_INCREMENT PRIMARY KEY
- `occurred_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
- `actor_user_id` BIGINT NULL
- `actor_username` VARCHAR(150) NOT NULL DEFAULT 'SYSTEM'
- `action` VARCHAR(64) NOT NULL
- `target_user_id` BIGINT NULL
- `target_username` VARCHAR(150) NULL
- `ip` VARCHAR(45) NOT NULL DEFAULT ''
- `details` JSON NULL
- `success` BOOLEAN NOT NULL DEFAULT TRUE
- Indexes: `idx_audit_occurred (occurred_at)`, `idx_audit_actor (actor_user_id)`, `idx_audit_target (target_user_id)`, `idx_audit_action (action)`

---

## Phase 2: Security & Business Logic Rules

1. **Safety Constraints (Enforced in Service Layer):**
   - **Last Admin Protection:** The system prevents disabling, demoting, archiving, or locking out the last active `ADMIN`.
   - **Self Protection:** An admin cannot disable, demote, or archive their own account (profile editing and password changes are permitted).
   - **Account Status Gating:** Any user who is `disabled`, archived (`deleted_at != null`), locked (`locked_until > now`), or expired (`account_expires_at <= now`) is denied authentication and their active access tokens are immediately rejected.
   - **Token Version Invalidation:** Changing a user's password, resetting a password, demoting/promoting role, disabling the account, or calling "force logout" increments `token_version` on `app_user` and marks all active `user_session` records as revoked.
   - **Password Policy:** Minimum 8 characters in dev, minimum 12 characters in prod. Requires at least one letter and one number, disallows username matching, and checks against common weak passwords (`password`, `123456`, `admin123`, `qwerty`).

2. **Bootstrap Admin Protection:**
   - Default behavior: creates `admin` if not existing. Does NOT overwrite an existing password or role on startup unless `BOOTSTRAP_ADMIN_FORCE_RESET=true` is set.
   - Writes an audit log entry on startup when a bootstrap admin is created or force-reset.

3. **Login Hardening:**
   - Account lockout: 5 consecutive failures locks the account for 15 minutes (`locked_until = now + 15m`).
   - Successful login resets `failed_login_attempts` to 0.
   - `last_login_at` and `last_login_ip` recorded.
   - Session created in `user_session` with SHA-256 hash of refresh token.
   - If `must_change_password` is TRUE, the user is restricted from invoking business endpoints until password is changed via `POST /api/me/change-password`.

---

## Permission Matrix

| Endpoint | Method | Anonymous | STAFF | ADMIN | Notes |
| :--- | :--- | :---: | :---: | :---: | :--- |
| `/api/auth/login` | POST | Allow | Allow | Allow | Rate-limited & lock check |
| `/api/auth/refresh` | POST | Allow | Allow | Allow | Validates DB session & version |
| `/api/auth/logout` | POST | 401 | Allow | Allow | Revokes current session |
| `/api/me` | GET, PUT | 401 | Allow | Allow | View/edit own profile |
| `/api/me/change-password` | POST | 401 | Allow | Allow | Changes password & clears must-change flag |
| `/api/me/sessions` | GET | 401 | Allow | Allow | Lists active sessions |
| `/api/me/sessions/{id}` | DELETE | 401 | Allow | Allow | Revokes single session |
| `/api/me/logout-others` | POST | 401 | Allow | Allow | Revokes other sessions |
| `/api/users` | GET, POST | 401 | 403 | Allow | List & Create users |
| `/api/users/{id}` | GET, PUT | 401 | 403 | Allow | Detail & Edit user |
| `/api/users/{id}/disable` | POST | 401 | 403 | Allow | Disable user |
| `/api/users/{id}/enable` | POST | 401 | 403 | Allow | Enable user |
| `/api/users/{id}/unlock` | POST | 401 | 403 | Allow | Reset lockout |
| `/api/users/{id}/reset-password` | POST | 401 | 403 | Allow | Issues temp password |
| `/api/users/{id}/force-logout` | POST | 401 | 403 | Allow | Bumps token_version |
| `/api/users/{id}` | DELETE | 401 | 403 | Allow | Soft delete (archive) |
| `/api/users/{id}/restore` | POST | 401 | 403 | Allow | Restore archived user |
| `/api/users/{id}/sessions` | GET | 401 | 403 | Allow | View user sessions |
| `/api/users/{id}/sessions/{sid}`| DELETE | 401 | 403 | Allow | Revoke specific session |
| `/api/users/{id}/activity` | GET | 401 | 403 | Allow | User's transactions & check-ins |
| `/api/users/bulk` | POST | 401 | 403 | Allow | Bulk status/role operations |
| `/api/users/export` | GET | 401 | 403 | Allow | CSV export |
| `/api/users/import` | POST | 401 | 403 | Allow | CSV preview & import |
| `/api/audit-log` | GET | 401 | 403 | Allow | Query audit entries |
| `/api/audit-log/export` | GET | 401 | 403 | Allow | Audit log CSV export |

*Note on MANAGER role:* The prompt allows adding a MANAGER role only if it can be added without regressions. To keep existing RBAC contracts, fixtures, and golden tests rock-solid, we retain the canonical `ADMIN` and `STAFF` roles and defer `MANAGER`.

---

## Phase 3: Frontend Architecture & UI Design

### 1. Navigation & Routing
- New navigation item: `{ path: '/users', icon: 'bx-group', label: 'Users', adminOnly: true }`.
- Visible in sidebar and home screen only if `state.user.role === 'ADMIN'`.
- Route guard in `app.js`: if a STAFF user attempts to navigate to `#/users`, redirect to `#/` and show an access-denied toast.
- "My account" modal accessible from the topbar user-chip for all users.
- Forced password change screen: if `state.user.mustChangePassword` is true, a blocking modal/screen requires updating password before accessing the application.

### 2. Users Management Page (`/js/pages/users.js`)
- **Tabs/Sub-views:**
  - **Users Directory Tab:**
    - Filter toolbar: Search input (debounced by 300ms), Role filter dropdown (All, Admin, Staff), Status filter dropdown (All, Active, Disabled, Locked, Expired, Archived).
    - Actions bar: "Add User" button, "Import CSV" button, "Export CSV" button, Bulk actions dropdown (when rows selected).
    - Table view (and responsive cards on mobile): Checkbox, Avatar with initials, Name & Username, Role badge, Status badge with indicators, Last Login date, Created date, Actions menu (Edit, Reset password, Lockout toggle, Disable/Enable, Archive).
    - Pagination controls: Page size selector (10, 25, 50), Previous/Next, page numbers.
  - **Audit Log Tab:**
    - Filterable audit table (Actor, Action, Target, IP, Date/Time, Status).
    - "Export Audit Log" CSV button.
- **Modals & Drawers:**
  - **Create / Edit User Modal:** Username, Full Name, Email, Phone, Role, Notes, Expiration date, Password generation with strength meter, "Require password change at first login" toggle.
  - **User Details Drawer / Modal:**
    - Profile Overview & Quick status badges.
    - Security tab: Active sessions list with IP, User-Agent, Last Seen, and Revoke button. Force logout button. Password reset button.
    - Activity tab: Summary counts (Recharges processed, Check-ins processed, Total volume handled) and recent transaction list.
    - Audit Trail: Recent audit entries affecting this user.
  - **CSV Import Modal:** File drop area, validation table showing Valid and Invalid rows with specific validation errors, and confirmation to import. Displays generated temporary passwords with a "Download CSV" option.
  - **My Account Modal:** Profile details, Change Password with current password verification and strength meter, Active Sessions list with "Log out other devices".

### 3. Settings Page Clean-up
- Remove `#btn-header-add-user` from the header.
- Remove `#btn-aside-add-user` and the "User accounts" sidebar card from the Settings layout.
- Remove all unused modal HTML, event listeners, and dead code from `settings.js`.
- Clean up any corresponding CSS while preserving spacing and balanced layout.
