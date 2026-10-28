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
- **PowerShell:** `.\run.ps1` or `.\java\run.ps1`
- **Windows Command Prompt:** `run.bat`
- **Unix / macOS / WSL:** `./run.sh`
- **NPM Script:** `npm start`

The runner automates:
1. Environment preparation (copies `.env.example` to `.env` if not present with safe local defaults).
2. Starting and awaiting the MySQL 8.4 Docker container (`docker compose up -d --wait mysql`).
3. Verifying the Java 25 runtime environment.
4. Incremental packaging of `venuity.jar` when source files are updated.
5. Launching the single runnable JAR and serving the app at `http://localhost:8080`.

## 6. Bootstrap Authentication & Credentials
- Default admin credentials: `admin` / `admin` (configurable via `BOOTSTRAP_ADMIN_USER` and `BOOTSTRAP_ADMIN_PASSWORD`).
- `BootstrapAdminRunner` synchronizes and updates the admin password and permissions automatically on startup whenever bootstrap credentials are configured, preventing lockout even if legacy migrated fixture data previously contained a different password.

## 7. Clean Repository Layout
Only active, relevant files remain in the root directory:
- `java/`: Complete Java 25 source, Maven configuration, and modules.
- `docs/`: Migration plans, ADRs, and technical documentation.
- `run.ps1`, `run.bat`, `run.sh`: Single-command multi-platform launchers.
- `venuity.jar`: Standalone single-JAR executable.
- `PROJECT_BRIEF.MD`, `README.md`, `package.json`, `.gitignore`: Root project metadata and configs.
- `legacy/`: Preserved archive of all deprecated/legacy directories and files (`backend/`, `frontend/`, `frontend_old/`, `src-tauri/`, `legacy_assets/`, `downloads/`, and legacy config files) preserving their exact file structure; completely excluded from git tracking via `.gitignore`.
