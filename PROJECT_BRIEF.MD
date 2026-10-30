# PROJECT BRIEF: Venuity Java Migration

## 1. Project Overview
Venuity is a PlayArea Manager platform designed for entertainment and play area businesses. It provides customer management, digital wallet recharge, QR-code based check-in and debiting, point-of-sale operations, business settings, and revenue/visitor analytics.

## 2. Migration Objective
Migrate the existing legacy stack (Django, SQLite, React Vite, Tauri 2) to a unified, production-ready **Java 25** application backed by **MySQL 8.4 LTS**.

## 3. Key Architecture & Constraints
- **Runtime:** Java 25 (Temurin OpenJDK 25 LTS).
- **Backend Framework:** Spring Boot 3.5.16 (Spring Web, Spring Security, Spring Data JPA, Spring Validation, Spring Boot Actuator).
- **Client / UI:** Embedded Single Page Application (HTML/CSS/JS with modern glassmorphism design, Chart.js analytics, jsQR scanning) served directly by Spring Boot from `src/main/resources/static/` (per ADR-013).
- **Database & Migration:** MySQL 8.4 with Flyway migrations (`V1__init.sql`, `V2__seed_settings.sql`).
- **Financial Safety:** `BigDecimal` with scale 2 (`HALF_UP`) for all currency operations. Check constraints enforce non-negative balances; all wallet balance adjustments are tracked via immutable ledger rows (`WalletTransaction`).
- **Authentication:** JWT (HS256) with ADMIN and STAFF roles, BCrypt password hashing with backward-compatible legacy PBKDF2 re-hashing upon login.
- **QR Compatibility:** Full backward compatibility with printed legacy customer QR codes (canonical dashed UUID and 32-hex format).

## 4. Single-JAR Distribution
The entire application (Spring Boot backend, Flyway database migrations, and bundled browser UI) packages into a single standalone executable JAR:
- **File:** `venuity.jar` (located at `java/venuity-server/target/venuity.jar` or repo root `venuity.jar`)
- **Execution:** `java -jar venuity.jar`

## 5. Single-Command Launchers
The application can be started using a single command from the project root or the `java/` folder:
- **PowerShell:** `.\run.ps1` or `.\java\run.ps1` (supports `-ForceRebuild` and `-SkipBuild` switches)
- **Windows Command Prompt:** `run.bat`
- **Unix / macOS / WSL:** `./run.sh`
- **NPM Script:** `npm start`

The runner automates:
1. Environment preparation (copies `.env.example` to `.env` if not present with safe local defaults).
2. Starting and awaiting the MySQL 8.4 Docker container (`docker compose up -d --wait mysql`).
3. Verifying the Java 25 runtime environment.
4. Incremental packaging of `venuity.jar` when source files are updated (`venuity-server/src`, `venuity-common/src`, `pom.xml`).
5. Process and port collision detection: checks for existing processes locking `venuity.jar` or listening on port 8080 before building or launching, preventing Windows file lock exceptions (`Unable to rename 'venuity.jar'`) and port bind conflicts.
6. Launching the single runnable JAR and serving the app at `http://localhost:8080`.

## 6. Bootstrap Authentication & Credentials
- Default admin credentials: `admin` / `admin` (configurable via `BOOTSTRAP_ADMIN_USER` and `BOOTSTRAP_ADMIN_PASSWORD`).
- `BootstrapAdminRunner` respects `BOOTSTRAP_ADMIN_FORCE_RESET`. In production, it defaults to `false` (only creating an initial admin if missing and preserving user-changed passwords across redeployments). In local development, it defaults to `true` to maintain convenient `admin/admin` developer defaults. Force-reset operations record an explicit audit log entry.

## 7. Clean Repository Layout
Only active, relevant files remain in the root directory:
- `java/`: Complete Java 25 source, Maven configuration, and modules.
- `docs/`: Migration plans, ADRs, and technical documentation.
- `run.ps1`, `run.bat`, `run.sh`: Single-command multi-platform launchers.
- `venuity.jar`: Standalone single-JAR executable.
- `PROJECT_BRIEF.MD`, `README.md`, `package.json`, `.gitignore`: Root project metadata and configs.
- `legacy/`: Preserved archive of all deprecated/legacy directories and files (`backend/`, `frontend/`, `frontend_old/`, `src-tauri/`, `legacy_assets/`, `downloads/`, and legacy config files) preserving their exact file structure; completely excluded from git tracking via `.gitignore`.

## 8. User Management UI & API
- **Top-Level "Users" Section:** Dedicated ADMIN-only navigation tab (`/users`) with full user lifecycle management.
- **Removed from Settings:** The legacy "Add user" header button and "User accounts" sidebar card have been cleanly removed from the Settings page.
- **User Directory:** Paginated list of users with search by name/username/email, role filtering (`ALL`, `ADMIN`, `STAFF`), status filtering (`ALL`, `ACTIVE`, `DISABLED`, `LOCKED`, `EXPIRED`, `ARCHIVED`), and multi-select.
- **User Actions:**
  - Create user with automatic temporary password generator or custom password, role assignment, and optional profile fields (full name, phone, notes).
  - Edit user profiles, phone, notes, and roles with last-active-admin protection.
  - Reset temporary password with forced change flag (`must_change_password`) and cryptographic password generation.
  - Disable / Enable accounts, Lock / Unlock accounts.
  - Soft-delete (Archive) and Restore accounts.
  - Bulk actions bar (Enable, Disable, Archive, Restore, Set Role).
- **Import & Export:**
  - CSV Export: Stream user directory to CSV with safe field projections (excluding password hashes and secrets).
  - CSV Import: Two-stage workflow with drag-and-drop file upload, dry-run schema and conflict validation preview with per-row status, and atomic commit generating temporary credentials.
- **User Activity & History Drawer:** Slide-out drawer showing user metadata, login timestamps, IP addresses, failed login counter, lockout state, and a 30-day cashier summary (top-up count & volume, check-in count & volume, recent transaction history).
- **Audit Logging:** Dedicated Audit Log tab (`/api/audit-log`) tracking administrative and authentication events (`LOGIN_SUCCESS`, `LOGIN_FAILED`, `ACCOUNT_LOCKOUT`, `USER_CREATED`, `USER_UPDATED`, `PASSWORD_RESET`, `BULK_ACTION`, etc.) with actor, target, timestamp, IP, details, and CSV export.
- **Security & Session Invalidation:**
  - Persistent refresh token tracking in `user_session` table using SHA-256 token hashing.
  - Instant token invalidation via `token_version` on password changes, user disablement, or session revocation.
  - Account lockout after 5 consecutive failed login attempts (15-minute lock), unlockable by administrators.
  - First-login forced password change gating at filter level with blocking modal prompt.
  - Self-service "My Account" profile and password update modal accessed via topbar user chip.

## 9. Seed & Analytics Demonstration Data
- **Timeframe:** January 1, 2026 to October 30, 2026 (~303 days).
- **Dataset:** 50 realistic customer accounts with unique UUIDs, phone numbers, and join dates; 8,843 wallet ledger transactions (`TOPUP` and `CHECKIN`); 7,982 verified check-in entries linked to transactions.
- **Traffic Modeling:** Weekend volume spikes (35-60 daily visits), summer vacation bump (May-June), holiday peaks (Republic Day, Independence Day, Dussehra/Diwali season), and diurnal peak curves (11:00-13:00 morning and 16:00-20:00 evening rush hours).
- **Generator Script:** `java/venuity-server/src/main/resources/db/seed_analytics_dummy_data.py`.
- **Analytics Visuals:** Fully populates Daily, Hourly, Weekly, and Monthly charts across Check-ins and Revenue in both modern and legacy analytics dashboards.

## 10. Free Cloud Hosting & Production Readiness
- **Managed MySQL with SSL:** Configured for cloud databases requiring SSL (e.g. Aiven Free MySQL with `DB_SSL_MODE=REQUIRED` by default in prod), dynamic port assignment, and non-default database names (e.g., `defaultdb`). Full `SPRING_DATASOURCE_URL` override supported.
- **Platform Port & Reverse Proxy:** Binds to `${PORT:${SERVER_PORT:8080}}`, with `server.forward-headers-strategy=framework` for TLS-terminating proxies and graceful shutdown (`server.shutdown=graceful`).
- **Security & Permissions Policy:** Enforces `Permissions-Policy: camera=(self)` to permit same-origin camera scanning over HTTPS while protecting the origin. Swagger UI / OpenAPI endpoints disabled by default in `prod`.
- **Production Safety Validator:** Startup validator active in `prod` profile that fails fast if `JWT_SECRET` is missing, shorter than 32 bytes, or matches dev defaults, or if bootstrap admin credentials are weak/missing.
- **Multi-Stage Container & Low-Memory Tuning:** Root `Dockerfile` using Temurin 25 JDK (build) and JRE (runtime) running as non-root user `venuity` (UID 10001). Low-memory settings (`-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError`, `DB_POOL_SIZE=5`, `spring.jpa.open-in-view=false`) tailored for 512 MB free tier hosts.
- **Deployment Blueprints & Documentation:** Complete guide in `docs/DEPLOYMENT.md`, Render blueprint in `render.yaml`, environment configuration in `render.env` (gitignored), systemd unit and Caddyfile in `deploy/`, and smoke testing script in `deploy/smoke-test.sh`.
- **Cloud Database Synchronization:** Full data migration executed from local MySQL to Aiven Managed MySQL (`defaultdb`), copying all 75 customers, 8,845 wallet ledger transactions, 7,983 check-in entries, idempotency records, business settings, and preserving user accounts (`admin`, `cashier1`, `sarang`, `aaron`).
