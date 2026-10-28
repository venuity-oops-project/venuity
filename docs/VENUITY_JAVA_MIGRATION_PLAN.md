# Venuity → Java Migration Plan (Agent Instructions)

> **Audience:** an autonomous coding agent (and any human reviewing its work).
> **Task:** rewrite the repository `https://github.com/aaronschristo/venuity` as an all-Java application (Swing UI, Spring Boot backend, MySQL database) and migrate its data.
> **Read this whole document before writing any code.** Work phase by phase. Do not skip verification gates.

---

## 0. Agent operating protocol

1. **Work in phases (Section 6 onward).** Finish a phase, pass its *Verification Gate*, commit, then start the next one.
2. **Never guess the source system.** The original behaviour lives in the Python and React code. Phase 0 requires you to read it and write down what it does. If this document and the source code disagree, **the source code wins**; record the discrepancy in `docs/migration/DISCREPANCIES.md`.
3. **Commit often** with Conventional Commit messages (`feat:`, `fix:`, `test:`, `chore:`, `docs:`). One logical change per commit.
4. **Keep a progress log** at `docs/migration/PROGRESS.md`. After each task, append: date, task ID, what changed, tests run, result.
5. **Record decisions** in `docs/migration/DECISIONS.md` (ADR style: context, decision, consequences).
6. **Stop and ask the human** (write the question in `docs/migration/QUESTIONS.md` and halt that task) only for the situations in Section 17. Otherwise use the defaults in Section 5 and continue.
7. **Never** commit secrets, real customer data, or `.env` files. Use `.env.example`.
8. **Never** use floating-point types for money. Use `BigDecimal` (Java) and `DECIMAL` (MySQL).
9. **Do not delete the old code** until Phase 9. Put the new code in a new top-level directory so both coexist.
10. **Run the build and tests before every commit.** A red build is never committed to the main working branch.

---

## 1. Mission and non-negotiable constraints

### Mission
Reproduce every user-visible feature and API behaviour of Venuity (a PlayArea manager: customer check-ins, wallet top-ups, QR passes, analytics, settings) in **Java only**, backed by **MySQL**, and migrate existing data without losing a single wallet balance.

### Non-negotiables

| # | Constraint |
|---|---|
| C1 | **Language:** all application code is Java. SQL is allowed for schema, migrations, views. Config files (`.properties`, `.xml`, `.yml`) are allowed. **No** JavaScript, TypeScript, Python, Rust or CSS in the new application. |
| C2 | **UI:** Java **Swing** (desktop). No web UI, no JavaFX, no Electron, no Tauri. |
| C3 | **Database:** **MySQL 8.4 LTS** (InnoDB, `utf8mb4`). No SQLite, H2 or other databases in production paths. (H2 is not allowed in tests either. Use Testcontainers MySQL.) |
| C4 | **Runtime:** the app runs on a plain JVM (Java 25). **No `.exe`, no installer, no `jpackage`.** Deliverables are runnable JARs started with `java -jar`. |
| C5 | **Money safety:** a wallet balance can never go negative; every balance change has a matching ledger row; no double-charging on retries. |
| C6 | **Data integrity:** after migration, every customer's balance equals the old balance exactly (to the cent). |
| C7 | **Feature parity:** every screen and endpoint in the old system has a working equivalent. Intentional omissions must be listed in `DECISIONS.md`. |

> **Plan amendment (2026-10-28):** The product owner overrode C1/C2. The Spring Boot server now serves a static HTML/CSS/JS client from `java/venuity-server/src/main/resources/static/`; the Swing client is superseded. See ADR-013. The Java server, MySQL, `BigDecimal` money handling and wallet-safety constraints remain unchanged.

---

## 2. Source system summary

### 2.1 What is known (from the README)

| Item | Detail |
|---|---|
| Name | Venuity – PlayArea Manager |
| Purpose | Digital customer check-ins, wallet top-ups, point-of-sale, QR code scanning |
| Frontend | React (Vite) + Vanilla CSS with a glassmorphism look; Axios in `frontend/src/lib/api.js` (10 s timeout) |
| Backend | Django REST Framework (README also mentions Flask) in `backend/`; SQLite `db.sqlite3` |
| Desktop wrapper | Tauri 2 (Rust) in `src-tauri/` |
| Other folders | `frontend_old/`, `legacy_assets/`, `docs/`, `downloads/`, `.github/workflows/`, `docker-compose.yml`, `.release-it.json` |
| Hosted API | `https://aaronschristo.pythonanywhere.com/api/` (do not call production for write tests) |

### 2.2 Documented screens and flow
1. **Settings:** business name, currency symbol, standard check-in fee.
2. **Customers:** create and list customer accounts (each has a QR pass).
3. **Recharge:** add money to a customer's wallet; supports QR auto-fill.
4. **Check-in:** scan a customer's QR with the camera; the wallet is debited automatically.
5. **Analytics:** revenue and visitor counts with interactive charts.

### 2.3 What is NOT known (you must discover in Phase 0)
Exact Django models and field names; exact URL routes and JSON shapes; authentication method; QR payload format; whether history/transactions are stored; pagination, filtering and validation rules; error message wording; any features in `src-tauri/` (printing, file dialogs, auto-update); purpose of `downloads/`, `docs/`, `scratch_test_analytics.py`, `shell_test.py`, `.test-sprite.json`.

---

## 3. Target stack (verify versions at build time)

Always look up the latest **stable** release of each dependency when creating the POM. Never use alpha, beta, milestone or RC versions. Record the chosen versions in `DECISIONS.md`.

| Concern | Choice |
|---|---|
| JDK | Java **25** (LTS) |
| Build | **Maven** 3.9+, multi-module, with Maven Wrapper (`mvnw`) committed |
| Backend framework | **Spring Boot 3.x** (latest stable 3.x line): Web, Data JPA, Validation, Security, Actuator |
| Database | **MySQL 8.4 LTS**; driver `com.mysql:mysql-connector-j` |
| Migrations | **Flyway** (`flyway-core`, `flyway-mysql`), SQL-only migrations |
| Auth | Spring Security + **JWT** (`io.jsonwebtoken:jjwt` 0.12.x) + BCrypt |
| API docs | `springdoc-openapi-starter-webmvc-ui` |
| DTO mapping | MapStruct (or hand-written mappers if you prefer fewer annotation processors) |
| Shared DTOs | Java `record`s in `venuity-common` |
| UI toolkit | **Swing** + **FlatLaf** (`flatlaf`, `flatlaf-extras`, `flatlaf-intellij-themes`) |
| Layout | **MigLayout** (`miglayout-swing`) |
| Charts | **JFreeChart** 1.5.x |
| QR encode/decode | **ZXing** (`com.google.zxing:core`, `javase`) |
| Camera | `com.github.sarxos:webcam-capture`. If native loading fails on a target OS, fall back to a maintained fork (for example `io.github.eduramiba:webcam-capture`) and document the swap in `DECISIONS.md` |
| Date picker | LGoodDatePicker (or `jdatepicker`) |
| HTTP client | `java.net.http.HttpClient` |
| JSON | Jackson |
| Logging | SLF4J + Logback |
| Tests | JUnit 5, Mockito, AssertJ, Testcontainers (MySQL), WireMock or OkHttp `MockWebServer`, AssertJ-Swing (UI smoke tests) |
| Packaging | `spring-boot-maven-plugin` (server fat JAR), `maven-shade-plugin` (client fat JAR) |
| CI | GitHub Actions |

---

## 4. Target repository layout

Create this inside the existing repo under `java/` (so old and new coexist until cutover):

```
venuity/
├── (existing python/react/tauri code, untouched until Phase 9)
└── java/
    ├── mvnw, mvnw.cmd, .mvn/
    ├── pom.xml                         # parent POM (packaging=pom, dependencyManagement, plugin versions)
    ├── README.md                       # how to build/run (Phase 8)
    ├── .env.example
    ├── docker-compose.yml              # mysql (+ optional server)
    ├── venuity-common/                 # shared DTO records, enums, error codes
    │   └── src/main/java/com/venuity/common/...
    ├── venuity-server/                 # Spring Boot REST API
    │   ├── src/main/java/com/venuity/server/
    │   │   ├── VenuityServerApplication.java
    │   │   ├── config/                 # SecurityConfig, JwtProperties, CorsConfig (if needed), JacksonConfig
    │   │   ├── user/                   # AppUser, AppUserRepository, AuthController, AuthService, JwtService
    │   │   ├── settings/               # BusinessSettings, repo, service, controller
    │   │   ├── customer/               # Customer, repo, service, controller, QrService
    │   │   ├── wallet/                 # WalletTransaction, repo, WalletService, controller
    │   │   ├── checkin/                # CheckIn, repo, CheckInService, controller
    │   │   ├── analytics/              # AnalyticsService, AnalyticsController, projections
    │   │   ├── idempotency/            # IdempotencyRecord, filter/service
    │   │   └── error/                  # ApiException, ErrorCode, GlobalExceptionHandler
    │   ├── src/main/resources/
    │   │   ├── application.yml, application-dev.yml, application-prod.yml
    │   │   └── db/migration/V1__init.sql, V2__..., ...
    │   └── src/test/java/...
    ├── venuity-client/                 # Swing application
    │   └── src/main/java/com/venuity/client/
    │       ├── app/                    # Main, AppConfig, Session
    │       ├── api/                    # ApiClient + per-feature API classes
    │       ├── ui/{shell,common,settings,customers,recharge,checkin,analytics,theme}/
    │       └── util/                   # Money, Debounce, Async, ImageUtil
    ├── venuity-migrate/                # one-off SQLite → MySQL migration tool (CLI)
    └── docs/migration/                 # INVENTORY.md, DECISIONS.md, PROGRESS.md, QUESTIONS.md, DISCREPANCIES.md, golden/
```

Java package root: `com.venuity`. Organise server code by **feature**, not by technical layer.

---

## 5. Decisions and defaults (use these unless the human overrides)

| # | Question | Default |
|---|---|---|
| D1 | One PC or several sharing data? | **Several.** Keep the three-tier design: Swing client → REST API → MySQL. Clients never get DB credentials. |
| D2 | Must already-printed QR passes keep working? | **Yes.** Preserve the old QR payload format exactly. Determine it in Phase 0. New customers use the same format. |
| D3 | Auth model | If the old system has none, still add login (ADMIN and STAFF roles) and note this as an intentional addition. If it has one, match its semantics. |
| D4 | Offline support in the client | **No.** If the server is unreachable, show a clear "server unreachable" state and block money operations. |
| D5 | Timezone | Store all timestamps in **UTC**. Display in the client's local zone. JDBC URL has `connectionTimeZone=UTC`. |
| D6 | Currency | Single currency configured in Settings (symbol only), as in the original. |
| D7 | Customer deletion | **Soft delete** (`active=false`). Never hard-delete customers who have ledger rows. |
| D8 | Legacy folders (`frontend_old`, `legacy_assets`, scratch scripts) | Do not port. Inspect `legacy_assets` only for data or QR format clues. |
| D9 | Hosting | Not decided. Produce a Dockerfile for the server and a plain `java -jar` path. No PythonAnywhere (cannot run Spring Boot). |

---

## 6. PHASE 0 — Inventory and baseline (do this first)

**Goal:** understand the old system precisely and capture a behavioural baseline.

### Tasks

**P0-1. Clone and read.** Clone the repo. Do not modify it. Read everything under `backend/`, `frontend/src/`, `src-tauri/` (only `src/` and `tauri.conf.json`), `docs/`, `requirements.txt`, `docker-compose.yml`, `TODO.txt`, `.github/workflows/`.

**P0-2. Backend inventory.** Produce `docs/migration/INVENTORY.md` with these sections:
- **Models:** each model, every field (name, type, nullability, default, unique, max length), relationships, and Meta options (ordering, constraints).
- **Endpoints:** table of `METHOD | path | auth required | request JSON | response JSON | status codes | validation rules | side effects`.
- **Business rules:** exactly how check-in debits the wallet (fee source, rounding, insufficient balance behaviour, duplicate-scan behaviour), how recharge works (bounds, bonus rules if any), how analytics are computed (grouping, time zone, date ranges).
- **Auth:** mechanism, token lifetime, user model, permissions.
- **QR payload:** what exactly is encoded (raw ID? UUID? JSON? URL?), and how the frontend parses it.
- **Settings:** storage (single row? key-value?) and defaults.

**P0-3. Frontend inventory.** For every page and component in `frontend/src`: purpose, API calls made, form fields and validation, tables and their columns, charts and their data, local state, error and empty states, strings shown to users. Append to `INVENTORY.md` under "Screens".

**P0-4. Tauri inventory.** List any native capabilities used (file dialogs, printing, notifications, auto-update, protocol handlers). Decide their Swing equivalents in `DECISIONS.md`.

**P0-5. Run the old system locally** (Python venv, `pip install -r requirements.txt`, `python manage.py migrate`, `runserver`) against a **throwaway copy** of the DB, never production. If you cannot run it, say so in `QUESTIONS.md` and continue from code reading.

**P0-6. Golden files.** With the old backend running locally, script calls (curl or a small throwaway script kept under `docs/migration/golden/tools/`) for every endpoint (success and error cases) and save the request and response pairs as JSON in `docs/migration/golden/`. These are the behavioural contract for Phase 3.

**P0-7. Data sample.** Copy `db.sqlite3` (or build a synthetic one from the models if no real data is committed) into `docs/migration/golden/sample.sqlite3`. Create 10+ customers with varied balances including `0.00`, odd decimals, large values and unicode names. **If real customer data exists in the repo, do not commit it elsewhere; use anonymised synthetic data for all fixtures.**

**P0-8. Reconcile this document with reality.** Update Sections 5 and 9 of *your working copy* of this plan if the real model differs, and log each difference in `DISCREPANCIES.md`.

### Verification Gate 0
- [ ] `INVENTORY.md` covers all models, endpoints, screens, auth, QR format, business rules.
- [ ] Golden files exist for every endpoint.
- [ ] Sample SQLite DB exists with varied data.
- [ ] `DECISIONS.md` has D2 (QR format) and D3 (auth) resolved from evidence.

---

## 7. PHASE 1 — Project skeleton, MySQL, CI

### Tasks

**P1-1. Parent POM.** Java 25 (`maven.compiler.release=25`), UTF-8, `dependencyManagement` (Spring Boot BOM, Testcontainers BOM), pinned plugin versions (compiler, surefire, failsafe, shade, spring-boot, enforcer). Modules: `venuity-common`, `venuity-server`, `venuity-client`, `venuity-migrate`. Enable `maven-enforcer-plugin` requiring Java 25+ and Maven 3.9+.

**P1-2. Module POMs.**
- `venuity-common`: Jackson annotations only, no Spring dependency.
- `venuity-server`: depends on `venuity-common`; Spring starters listed in Section 3; `spring-boot-maven-plugin` repackage.
- `venuity-client`: depends on `venuity-common`; FlatLaf, MigLayout, JFreeChart, ZXing, webcam-capture, Jackson, SLF4J/Logback; `maven-shade-plugin` with `ManifestResourceTransformer` main class `com.venuity.client.app.Main`, plus `ServicesResourceTransformer` and signature-file excludes (`META-INF/*.SF|DSA|RSA`).
- `venuity-migrate`: SQLite JDBC (`org.xerial:sqlite-jdbc`), MySQL connector, picocli; shade plugin for a runnable JAR.

**P1-3. Docker Compose** (`java/docker-compose.yml`):
```yaml
services:
  mysql:
    image: mysql:8.4
    container_name: venuity-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: venuity
      MYSQL_USER: venuity
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
    command: ["--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci", "--default-time-zone=+00:00"]
    ports: ["3306:3306"]
    volumes: ["venuity-mysql-data:/var/lib/mysql"]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 10
volumes:
  venuity-mysql-data:
```
Provide `.env.example` with placeholder values only.

**P1-4. Server configuration.** `application.yml` with profiles. Datasource URL: `jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/venuity?connectionTimeZone=UTC&useUnicode=true&characterEncoding=utf8`. `spring.jpa.hibernate.ddl-auto=validate` (**never** `update` or `create`). `spring.flyway.enabled=true`. `spring.jpa.open-in-view=false`. Secrets from environment variables.

**P1-5. CI** (`.github/workflows/java-ci.yml`): triggers on push and PR touching `java/**`; Temurin 25; cache Maven; run `./mvnw -B verify` (Testcontainers needs Docker, which is available on `ubuntu-latest`); upload test reports on failure; upload the single runnable server JAR as the release artifact.

### Verification Gate 1
- [ ] `./mvnw -B clean verify` succeeds from `java/`.
- [ ] `docker compose up -d mysql` becomes healthy.
- [ ] Server starts, Flyway runs `V1`, `/actuator/health` returns `UP`.
- [ ] CI is green.

---

## 8. PHASE 2 — Database schema (Flyway SQL)

Write schema as `venuity-server/src/main/resources/db/migration/V1__init.sql`. **Adjust column names and types to match the real Django models found in Phase 0** (keep names descriptive and snake_case). The reference schema below is the intended shape:

```sql
-- V1__init.sql
SET NAMES utf8mb4;

CREATE TABLE app_user (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(80)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,             -- BCrypt (60 chars) or a legacy-marked hash, see Phase 4
  role          ENUM('ADMIN','STAFF') NOT NULL DEFAULT 'STAFF',
  enabled       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT uq_app_user_username UNIQUE (username)
) ENGINE=InnoDB;

CREATE TABLE business_settings (
  id              TINYINT PRIMARY KEY,
  business_name   VARCHAR(150) NOT NULL,
  currency_symbol VARCHAR(8)   NOT NULL,
  checkin_fee     DECIMAL(10,2) NOT NULL,
  updated_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT ck_settings_single_row CHECK (id = 1),
  CONSTRAINT ck_settings_fee_nonneg CHECK (checkin_fee >= 0)
) ENGINE=InnoDB;

CREATE TABLE customer (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  public_code    VARCHAR(64)  NOT NULL,            -- the value encoded in the QR (format from Phase 0)
  name           VARCHAR(150) NOT NULL,
  phone          VARCHAR(30)  NULL,
  wallet_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  active         BOOLEAN NOT NULL DEFAULT TRUE,
  version        BIGINT  NOT NULL DEFAULT 0,       -- JPA @Version
  created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT uq_customer_public_code UNIQUE (public_code),
  CONSTRAINT ck_customer_balance_nonneg CHECK (wallet_balance >= 0),
  INDEX idx_customer_name (name),
  INDEX idx_customer_phone (phone)
) ENGINE=InnoDB;

-- Append-only ledger. Never UPDATE or DELETE rows in application code.
CREATE TABLE wallet_transaction (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id   BIGINT NOT NULL,
  type          ENUM('TOPUP','CHECKIN','ADJUSTMENT','REFUND','OPENING_BALANCE') NOT NULL,
  amount        DECIMAL(12,2) NOT NULL,            -- signed: credit > 0, debit < 0
  balance_after DECIMAL(12,2) NOT NULL,
  note          VARCHAR(255) NULL,
  created_by    BIGINT NULL,
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_tx_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
  CONSTRAINT fk_tx_user     FOREIGN KEY (created_by)  REFERENCES app_user(id),
  CONSTRAINT ck_tx_balance_after_nonneg CHECK (balance_after >= 0),
  INDEX idx_tx_customer_time (customer_id, created_at),
  INDEX idx_tx_type_time (type, created_at)
) ENGINE=InnoDB;

CREATE TABLE check_in (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id    BIGINT NOT NULL,
  transaction_id BIGINT NOT NULL,
  fee_charged    DECIMAL(10,2) NOT NULL,
  checked_in_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_checkin_customer FOREIGN KEY (customer_id)    REFERENCES customer(id),
  CONSTRAINT fk_checkin_tx       FOREIGN KEY (transaction_id) REFERENCES wallet_transaction(id),
  CONSTRAINT uq_checkin_tx UNIQUE (transaction_id),
  INDEX idx_checkin_time (checked_in_at),
  INDEX idx_checkin_customer_time (customer_id, checked_in_at)
) ENGINE=InnoDB;

-- Idempotency for money-moving POSTs
CREATE TABLE idempotency_record (
  idem_key      CHAR(36)     NOT NULL,
  endpoint      VARCHAR(100) NOT NULL,
  user_id       BIGINT       NOT NULL,
  request_hash  CHAR(64)     NOT NULL,
  response_code INT          NULL,
  response_body JSON         NULL,
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (idem_key, endpoint, user_id),
  INDEX idx_idem_created (created_at)
) ENGINE=InnoDB;

CREATE OR REPLACE VIEW v_daily_stats AS
SELECT DATE(checked_in_at) AS day, COUNT(*) AS visits, SUM(fee_charged) AS checkin_revenue
FROM check_in GROUP BY DATE(checked_in_at);
```

Rules:
- If Phase 0 shows extra fields (address, notes, birthday, etc.), add them. If the old app has no history table, keep the ledger anyway (it is the integrity backbone) and use `OPENING_BALANCE` rows during migration.
- Seed the settings row in `V2__seed_settings.sql`: `INSERT INTO business_settings (id, business_name, currency_symbol, checkin_fee) VALUES (1, 'My PlayArea', '₹', 0.00);` (use the real defaults from the old code if found).
- Never edit an applied migration. Add a new `V{n}__description.sql`.
- Define analytics so that **"revenue" matches the old system's definition** (it may include top-ups or only check-in fees; follow Phase 0 findings).

### Verification Gate 2
- [ ] Flyway applies cleanly on an empty MySQL; `ddl-auto=validate` passes against the entities written in Phase 3.
- [ ] A Testcontainers test confirms the `CHECK` constraints reject a negative balance and a second settings row.

---

## 9. PHASE 3 — Backend (Spring Boot)

### 9.1 Conventions
- Constructor injection only. DTOs are Java `record`s with Bean Validation annotations. Entities never leave the service layer.
- Money type: `BigDecimal` with `scale=2`, rounding `RoundingMode.HALF_UP`, normalised in one helper `MoneyUtil.normalize(BigDecimal)`.
- Time type: `Instant` (UTC) in entities and DTOs.
- Controllers are thin; business logic lives in `@Service` classes.
- All error responses use the shape `{ "code": "INSUFFICIENT_BALANCE", "message": "...", "timestamp": "...", "details": {...} }`.

### 9.2 Error codes (`venuity-common`)
`VALIDATION_FAILED`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CUSTOMER_INACTIVE`, `INVALID_QR`, `INSUFFICIENT_BALANCE`, `AMOUNT_OUT_OF_RANGE`, `IDEMPOTENCY_CONFLICT`, `INTERNAL_ERROR`. Map to HTTP: 400, 401, 403, 404, 409, 422 as appropriate. Match the old system's status codes where the golden files show them.

### 9.3 API surface
Match old routes and JSON field names from the golden files where they exist (the Swing client is new, so a clean contract is acceptable, but **preserve the old shapes if any other consumer might call them**). Proposed surface:

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/login` | returns `{accessToken, expiresAt, user}` |
| GET | `/api/auth/me` | current user |
| GET | `/api/health` | public; `{status:"UP", version}` |
| GET / PUT | `/api/settings` | PUT requires ADMIN |
| GET | `/api/customers?query=&page=&size=&active=` | paginated, search by name/phone/code |
| POST | `/api/customers` | create; server generates `public_code` in the Phase 0 format |
| GET / PUT | `/api/customers/{id}` | |
| DELETE | `/api/customers/{id}` | soft delete (deactivate) |
| GET | `/api/customers/by-code/{code}` | used by scanner/recharge auto-fill |
| GET | `/api/customers/{id}/qr.png?size=` | ZXing PNG |
| POST | `/api/customers/{id}/recharge` | requires `Idempotency-Key` header |
| POST | `/api/checkins` | body `{code}`; requires `Idempotency-Key` header |
| GET | `/api/transactions?customerId=&type=&from=&to=&page=&size=` | |
| GET | `/api/analytics/summary?from=&to=` | totals and averages |
| GET | `/api/analytics/daily?from=&to=` | per-day visits and revenue |
| GET | `/api/analytics/hourly?from=&to=` | visits by hour of day |

### 9.4 The two money-moving methods (implement exactly like this)

```java
@Service
@RequiredArgsConstructor // or explicit constructor
public class WalletService {

  private final CustomerRepository customers;
  private final WalletTransactionRepository ledger;

  @Transactional
  public WalletTransaction topUp(long customerId, BigDecimal amount, Long userId, String note) {
    BigDecimal amt = MoneyUtil.normalize(amount);
    if (amt.signum() <= 0 || amt.compareTo(MAX_TOPUP) > 0) {
      throw new ApiException(ErrorCode.AMOUNT_OUT_OF_RANGE, "Amount out of range");
    }
    Customer c = customers.findByIdForUpdate(customerId)           // @Lock(PESSIMISTIC_WRITE)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Customer not found"));
    if (!c.isActive()) throw new ApiException(ErrorCode.CUSTOMER_INACTIVE, "Customer inactive");
    BigDecimal newBalance = c.getWalletBalance().add(amt);
    c.setWalletBalance(newBalance);
    return ledger.save(WalletTransaction.of(c, TxType.TOPUP, amt, newBalance, note, userId));
  }

  @Transactional
  public WalletTransaction debit(long customerId, BigDecimal fee, TxType type, Long userId, String note) {
    Customer c = customers.findByIdForUpdate(customerId).orElseThrow(...);
    if (!c.isActive()) throw new ApiException(ErrorCode.CUSTOMER_INACTIVE, ...);
    BigDecimal newBalance = c.getWalletBalance().subtract(fee);
    if (newBalance.signum() < 0) throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE, "Insufficient balance");
    c.setWalletBalance(newBalance);
    return ledger.save(WalletTransaction.of(c, type, fee.negate(), newBalance, note, userId));
  }
}
```

`CheckInService.checkIn(code, userId)` runs in **one** `@Transactional` method: resolve customer by `public_code` (invalid → `INVALID_QR`), read current fee from settings, call `debit(...)`, insert the `check_in` row, return a DTO `{customerName, feeCharged, balanceAfter, checkedInAt}`. Apply the old system's duplicate-scan rule if one exists (Phase 0).

Repository lock query:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select c from Customer c where c.id = :id")
Optional<Customer> findByIdForUpdate(@Param("id") long id);
```
The `Customer` entity has `@Version private long version;` as a backstop.

### 9.5 Idempotency
Implement a servlet filter or an `@Around` aspect for endpoints annotated `@Idempotent`:
1. Require header `Idempotency-Key` (UUID); otherwise 400.
2. Compute SHA-256 of the request body.
3. Insert into `idempotency_record` (PK conflict means a duplicate).
4. If the key exists with the same hash and a stored response, **replay** the stored response; if it exists with a different hash → `IDEMPOTENCY_CONFLICT` (409); if it exists with no response yet (in flight) → 409 retry-later.
5. After success, store the response code and body. A scheduled job deletes records older than 48 hours.

### 9.6 Security
- BCrypt (strength 10–12). JWT HS256 with a secret from `JWT_SECRET` (min 32 bytes), expiry from config (default 12 h).
- Public: `/api/auth/login`, `/api/health`, `/actuator/health`. Everything else authenticated. `PUT /api/settings`, user management and ADJUSTMENT/REFUND operations require ADMIN.
- Stateless sessions, CSRF disabled (token API, no cookies), security headers on, request size limits.
- Login rate limiting (Bucket4j or a simple in-memory limiter): 5 failures per minute per IP+username.
- Bootstrap: if no users exist at startup and `BOOTSTRAP_ADMIN_USER`/`BOOTSTRAP_ADMIN_PASSWORD` env vars are set, create the first ADMIN. Never ship default passwords.

### 9.7 QR generation
`QrService.png(String payload, int size)` using ZXing `QRCodeWriter` with error correction level `M` and margin of 2, returning PNG bytes. **The payload must be byte-for-byte compatible with the old system's QR content** (Phase 0, D2).

### 9.8 Analytics
Use JPQL or native SQL aggregations and DTO projections. Group by the **configured business time zone** (add `business_timezone` to settings, default `Asia/Kolkata`; confirm against old behaviour) using `CONVERT_TZ` or by querying UTC ranges computed in Java. Return zero-filled series for days with no data so charts have no gaps.

### 9.9 Tests (required)
- Unit tests for `WalletService`, `CheckInService`, `MoneyUtil`, QR payload round-trip.
- `@DataJpaTest` or `@SpringBootTest` with **Testcontainers MySQL** for repositories and the full flows.
- **Concurrency test:** 50 threads call `checkIn` for one customer whose balance covers exactly 10 check-ins. Assert exactly 10 succeed, 40 get `INSUFFICIENT_BALANCE`, and the final balance is `0.00`.
- **Invariant test:** after random operations, `customer.wallet_balance = SUM(wallet_transaction.amount)` for every customer.
- **Idempotency test:** the same key twice yields one ledger row and identical responses.
- **Contract tests:** replay golden requests against the new server; compare status codes and JSON shape (field names/types; ignore volatile fields like ids and timestamps).
- Security tests: unauthenticated and wrong-role requests are rejected.

### Verification Gate 3
- [x] All endpoints implemented; OpenAPI UI loads at `/swagger-ui.html`.
- [x] Contract tests pass for all golden files (or deviations are justified in `DISCREPANCIES.md`).
- [x] Concurrency, invariant and idempotency tests pass.
- [x] `./mvnw -B verify` is green, and test coverage for `wallet` and `checkin` packages is ≥ 85% line coverage (JaCoCo).

---

## 10. PHASE 4 — Data migration tool (`venuity-migrate`)

Can run in parallel with Phase 3 once Phase 2 is done.

### 10.1 CLI
picocli command: `java -jar venuity-migrate.jar --sqlite path/to/db.sqlite3 --mysql-url jdbc:mysql://... --mysql-user ... --mysql-password-env MYSQL_PASSWORD [--dry-run] [--report out.md]`.

### 10.2 Algorithm
1. **Preflight:** open SQLite read-only; connect to MySQL; verify target tables are empty (refuse otherwise unless `--force-empty-check-off`, which transactionally deletes existing migrated rows before import). Print row counts.
2. **Read** Django tables (names from Phase 0, e.g. `api_customer`, `auth_user`, …) with plain JDBC.
3. **Transform:**
   - Floats/decimals in SQLite → `BigDecimal` via `new BigDecimal(String)` of the textual value (never `double`), scale 2, HALF_UP. Log any value that changes when rounded.
   - Datetimes: treat naive timestamps as the Django `TIME_ZONE` (read from settings; default UTC), convert to UTC.
   - QR identifiers: copy the **existing** identifier into `customer.public_code` unchanged. If the old system had none, generate it in the Phase 0 format.
   - Users: Django hashes look like `pbkdf2_sha256$<iterations>$<salt>$<hash>`. Store them as-is in `password_hash` and implement a `LegacyDjangoPasswordEncoder` (PBKDF2-HMAC-SHA256, Base64 output) inside a `DelegatingPasswordEncoder` keyed by `{django}`; on a successful login, **re-hash with BCrypt** and update the row. If this is judged too risky, instead create users with random passwords and require a reset, and record it in `DECISIONS.md`.
   - Settings: map to the single `business_settings` row.
4. **Write** in batches (500 rows) inside one MySQL transaction per table, in dependency order: `app_user` → `business_settings` → `customer` → `wallet_transaction` → `check_in`.
5. **Ledger backfill:**
   - If the old system has transaction/recharge/check-in history: import it in chronological order, recomputing `balance_after` as a running total. If the running total's final value ≠ the old balance, insert one `ADJUSTMENT` row with note `Migration reconciliation` for the difference and list it in the report.
   - If the old system has no history: insert one `OPENING_BALANCE` row per customer with `amount = old balance` (skip if the balance is zero).
6. **Reconcile** (automatic, end of run): per-table row counts; per-customer balance equality old vs new; sum of all balances old vs new; count of customers with `balance != SUM(ledger)`. Any mismatch → non-zero exit code and a detailed report.
7. **`--dry-run`:** perform every step inside a transaction that is rolled back, and still print the full report.

### 10.3 Tests
- Run the tool against `golden/sample.sqlite3` in a Testcontainers MySQL; assert the reconciliation report is clean.
- Include edge-case fixtures: zero balance, balance with many decimals (`12.345`), huge balance, unicode names, NULL phone, duplicate-looking names.
- Idempotency of the tool: running it twice must fail the preflight on the second run, not duplicate data.

### Verification Gate 4
- [x] Migration of the sample DB reconciles with zero differences.
- [x] Re-running is blocked by preflight.
- [x] The migrated users can log in (legacy hash path) and are rehashed to BCrypt on login.

---

## 11. PHASE 5 — Swing foundation (client)

### 11.1 Bootstrapping (`Main`)
```java
public static void main(String[] args) {
  System.setProperty("flatlaf.useWindowDecorations", "true");
  FlatDarkLaf.setup();                     // theme from Theme/prefs
  SwingUtilities.invokeLater(() -> {
    AppConfig cfg = AppConfig.load();       // venuity.properties next to the JAR, or user home
    ApiClient api = new ApiClient(cfg.serverUrl());
    new LoginDialog(api, session -> new MainFrame(api, session, cfg).setVisible(true)).setVisible(true);
  });
}
```
`AppConfig` keys: `server.url` (default `http://localhost:8080`), `ui.theme` (`dark|light`), `camera.index`. Persist user choices with `java.util.prefs.Preferences` or a small properties file in the user's home directory.

### 11.2 Threading rule (strict)
**No HTTP, file or camera work on the Event Dispatch Thread.** All UI updates happen on the EDT. Provide one helper used by every screen:

```java
public final class Async {
  public static <T> void run(Supplier<T> work, Consumer<T> onOk, Consumer<Throwable> onErr) {
    new SwingWorker<T, Void>() {
      @Override protected T doInBackground() { return work.get(); }
      @Override protected void done() {
        try { onOk.accept(get()); }
        catch (Exception e) { onErr.accept(e.getCause() != null ? e.getCause() : e); }
      }
    }.execute();
  }
}
```
Add a unit test or ArchUnit rule that fails if code in `ui/**` calls `ApiClient` methods directly outside `Async.run` (or document this as a code-review checklist item if ArchUnit is impractical).

### 11.3 `ApiClient`
- `HttpClient` with 10 s connect timeout and 10 s per-request timeout; one shared instance.
- Adds `Authorization: Bearer <token>` automatically; on 401 triggers a re-login flow.
- Jackson `ObjectMapper` configured for `BigDecimal` (use `USE_BIG_DECIMAL_FOR_FLOATS`), `Instant`, ignoring unknown properties.
- Maps error JSON to `ApiException(code, message)` (client-side class using shared `ErrorCode`).
- **Retries:** GET only, max 2, with backoff. **Never auto-retry POST/PUT/DELETE.**
- Money-moving calls generate a fresh `Idempotency-Key` **per user action** (when the user clicks) and reuse the same key if the user explicitly presses "Retry" for that same action.
- `ping()` calls `/api/health`; the main frame shows a connection indicator and re-checks every 15 s.

### 11.4 Theme and "glassmorphism"
`Theme` holds colours, fonts, radii and spacing constants. Components:
- `GlassPanel`: overrides `paintComponent`; draws a rounded rectangle with an alpha white fill (≈ 8–12 %), a 1 px translucent border and a soft drop shadow; sets `RenderingHints.KEY_ANTIALIASING`.
- Background: a gradient painted on the content pane. If a blurred image is used, blur it **once** with a `ConvolveOp`, cache it, and never re-blur on repaint.
- Reusable widgets: `KpiCard`, `PrimaryButton`, `ToastNotifier` (non-modal, auto-dismiss), `BusyOverlay` (glass-pane spinner), `ConfirmDialog`, `FormField` (label + input + inline validation message).

### 11.5 Shell
`MainFrame`: left navigation sidebar (Check-in, Customers, Recharge, Analytics, Settings), a header with business name, currency, user, connection status and logout, and a `CardLayout` content area. Keyboard shortcuts: Ctrl+1…5 for screens, F5 to refresh, Esc to close dialogs. Respect accessibility basics (mnemonics, focus order, tooltips).

### Verification Gate 5
- [ ] App launches, shows login, authenticates against the local server, and shows the shell with navigation.
- [ ] Server-down state is shown gracefully (no stack traces, no frozen UI).
- [ ] Theme switch (dark/light) works at runtime.

---

## 12. PHASE 6 — Swing screens

Implement each screen as `XPanel` (view) + `XPresenter` (logic, no Swing imports, unit-testable) + a table model where relevant. **Every string, column, validation rule and empty-state must come from the Phase 0 screen inventory.** The bullets below are the required minimum.

### 12.1 Settings
Fields: business name, currency symbol, check-in fee (plus timezone if added). Validation: name not blank; fee parses to `BigDecimal` ≥ 0 with ≤ 2 decimals. ADMIN-only edit; STAFF sees read-only. Save → toast. Dirty-state indicator and "Reset" button.

### 12.2 Customers
- `JTable` with `CustomerTableModel`; columns per Phase 0 (name, phone, code, balance, status, created).
- Server-side pagination (page size 25) and a **debounced** (300 ms) search box.
- Dialogs: Add/Edit (with validation), Deactivate/Reactivate (confirm), and **QR dialog** that shows the PNG from `/qr.png` with *Save PNG…* (`JFileChooser`) and *Print…* (`PrinterJob` with `Printable`).
- Balance column right-aligned and formatted with the settings currency symbol using `Money.format`.
- Double-click a row → customer detail with recent transactions.

### 12.3 Recharge
Customer search (combo with async lookup), selected customer card (name, balance), amount field with quick-amount buttons (values per Phase 0 or 100/200/500/1000), confirm dialog showing before and after balance, receipt dialog (printable). **QR auto-fill:** a "Scan" button opens the scanner dialog and fills the customer from the decoded code (`/customers/by-code/{code}`). Disable the confirm button while a request is in flight.

### 12.4 Check-in (with camera)
`CameraScannerPanel`:
- Lists cameras (`Webcam.getWebcams()`), remembers the selection, opens at VGA, shows a live preview (~15–30 fps repaint, decode at ~5–10 fps).
- Decoding on a dedicated background thread using ZXing `MultiFormatReader` restricted to `QR_CODE` with `TRY_HARDER` off by default (retry with it on if scans are slow to succeed).
- **Debounce:** ignore the same code for 3 s. Show a brief "scanning paused" indication after a successful read.
- On code → `POST /api/checkins` (new idempotency key per scan) → `ResultCard`: green success (name, fee charged, new balance), red failure (insufficient balance with the shortfall amount, inactive customer, invalid QR). Keep the last 10 results in a side list.
- Manual entry field as a fallback (also handy for handheld USB scanners that type the code and press Enter).
- Always release the camera (`webcam.close()`) when the panel is hidden, the window closes, or an error occurs. Handle "no camera found" and "camera in use" with clear messages.
- Beep (`Toolkit.getDefaultToolkit().beep()` or a short bundled WAV via `javax.sound.sampled`) on success/failure.

### 12.5 Analytics
Date range picker with presets (Today, 7 days, 30 days, This month, Custom). KPI cards: total revenue, total visits, average revenue per visit, average visits per day (match the old definitions). Charts with JFreeChart in `ChartPanel`: line/area chart of daily revenue, bar chart of daily visits, bar chart of visits by hour. Dark/light chart styling that matches the FlatLaf theme. Zoom disabled, tooltips on. Show empty-state text when no data. Optional: export current view to CSV (`JFileChooser`).

### 12.6 UI tests
- Presenter unit tests with a mocked `ApiClient`.
- AssertJ-Swing smoke tests (run under `xvfb-run` in CI): login → open Customers → create a customer → recharge → verify balance label. Camera path is covered by a **fake `FrameSource`** interface so decode logic is testable with a static QR image.
- Manual QA checklist in `java/docs/QA_CHECKLIST.md`: camera on at least one Windows and one macOS/Linux machine; HiDPI at 125 % and 150 %; long names; 10,000 customers pagination.

### Verification Gate 6
- [ ] Every screen in the Phase 0 inventory exists and works end to end against the local server.
- [ ] Camera scan check-in debits correctly and shows the result; denial paths are handled.
- [ ] No EDT violations (manually verify with `-Dswing.debug` or a EDT-violation checker; code review checklist ticked).
- [ ] UI smoke tests pass in CI.

---

## 13. PHASE 7 — Hardening and quality

- **Server:** enable structured logging, correlation IDs, Actuator health and info, graceful shutdown, HikariCP tuning (pool size 10, timeouts), `EXPLAIN` the analytics and search queries and add indexes if needed, slow-query logging in dev.
- **Security review:** dependency vulnerability scan (`mvn org.owasp:dependency-check-maven:check` or GitHub Dependabot), no secrets in logs, HTTPS required in prod (document reverse proxy with TLS; client refuses plain HTTP to non-localhost unless `allow.insecure=true`).
- **Client:** global uncaught-exception handler that logs and shows a friendly dialog; log file rotation in the user's home; graceful behaviour on token expiry; app does not store the password; the token lives only in memory (or an OS-protected store if added).
- **Backups:** document `mysqldump --single-transaction` schedule and restore test.
- **Performance:** load test `POST /api/checkins` (Gatling or JMeter) at 20 concurrent users on one customer and on many customers; confirm no deadlocks and acceptable latency (p95 < 200 ms locally).
- **Docs:** `java/README.md`, `java/docs/ARCHITECTURE.md` (diagram in Mermaid), `java/docs/OPERATIONS.md` (backup, restore, rotate JWT secret, create users), `java/docs/QA_CHECKLIST.md`.

### Verification Gate 7
- [ ] Dependency scan shows no high/critical issues (or accepted risks documented).
- [ ] Load test report saved under `docs/`.
- [ ] Docs complete and a fresh clone can follow the README to a working system.

---

## 14. PHASE 8 — Packaging (Single JAR release and launchers)

1. `./mvnw -B -DskipTests=false clean verify` (or `-pl venuity-server -am -DskipTests package`) produces:
   - `venuity-server/target/venuity.jar` (single executable fat JAR containing Spring Boot backend, embedded static browser UI, and Flyway database migrations)
   - `venuity-client/target/venuity-client-<version>-shaded.jar` (retained Swing client)
   - `venuity-migrate/target/venuity-migrate-<version>-shaded.jar` (SQLite to MySQL migration tool)
2. **Run command** (single command to start MySQL, build if needed, and run the app):
   ```
   .\run.ps1
   ```
   or directly:
   ```
   docker compose up -d mysql
   java -jar venuity.jar --spring.profiles.active=prod
   ```
3. Provide single-command launcher scripts (`run.ps1`, `run.bat`, `run.sh`, `npm start`) that check `java -version` ≥ 25, automatically prepare environment defaults, spin up MySQL, package the single JAR if needed, and run `java -jar venuity.jar`.
4. Provide a server `Dockerfile` (multi-stage: `maven:3.9-eclipse-temurin-25` build, `eclipse-temurin:25-jre-jammy` runtime, non-root user, `HEALTHCHECK`) and an optional `server` service in `docker-compose.yml`.
5. CI release job: Temurin 25 build and attach the single runnable `venuity.jar` to GitHub Actions artifacts / Release.

### Verification Gate 8
- [x] On a clean machine with JDK 25 and Docker: running the single command `.\run.ps1` yields a working login, check-in, recharge, and analytics at http://localhost:8080.
- [x] Single runnable JAR `venuity.jar` is produced and verified on Java 25.
- [x] Release artifact is uploaded by CI.

---

## 15. PHASE 9 — Cutover

1. **Parallel run:** run the new stack against a copy of production data; compare analytics totals and balances with the old system for the same date ranges.
2. **Freeze window:** announce read-only on the old system; take a final SQLite backup.
3. **Final migration:** run `venuity-migrate` (`--dry-run` first, then for real); reconcile; keep the report in `docs/migration/FINAL_MIGRATION_REPORT.md`.
4. **Smoke test** on the new system (login, find a known customer, recharge 1 unit and reverse it with an ADJUSTMENT, scan a real pass).
5. **Switch** all counter PCs to the new client JAR and server URL.
6. **Rollback plan:** keep the old system read-only and the SQLite backup for at least 14 days. If a critical money-integrity bug appears, stop writes, re-run the reconciliation, and decide between a fix-forward or reverting to the old system.
7. **Cleanup (only after sign-off):** move legacy code to a `legacy/` branch or tag `python-react-final`, update the root `README.md` to describe the Java system, and remove obsolete CI workflows and `release-it` config. Do this in a separate PR.

---

## 16. Definition of Done

- [ ] All items from `INVENTORY.md` are implemented or explicitly waived in `DECISIONS.md`.
- [ ] Repository (excluding the preserved legacy code) has no JS/TS/Python/Rust/CSS in the new application; SQL, `.properties`, `.yml`, `.xml` only as config or schema.
- [ ] `./mvnw -B verify` is green locally and in CI; coverage target met for wallet and check-in code.
- [ ] Migration reconciliation report shows zero differences.
- [ ] The app runs with `java -jar` only; no `.exe` or installer exists in the build.
- [ ] Concurrency, idempotency and ledger-invariant tests pass.
- [ ] Documentation exists and has been validated from a fresh clone.

---

## 17. When to stop and ask the human

Write the question in `docs/migration/QUESTIONS.md`, pause only that task, and continue other independent work:

1. The source code reveals features that conflict with the constraints in Section 1 (for example, a web-only integration or payment gateway).
2. Real customer or production data appears in the repo, or credentials/secrets are found in the history. Report, do not copy.
3. The old system's business rules are contradictory or ambiguous (e.g. two different check-in fee calculations).
4. The QR format cannot be determined, or old QR passes cannot be reproduced exactly.
5. Reconciliation shows balance differences you cannot explain.
6. A required dependency is unavailable or has a licence incompatible with the project.
7. A decision in Section 5 turns out to be wrong based on evidence (for example, there is only ever one machine).

---

## 18. Risk register

| Risk | Impact | Mitigation |
|---|---|---|
| Unknown models/endpoints until Phase 0 | Schema/API rework | Phase 0 gate before any implementation; golden files |
| Old QR passes stop scanning | Customers locked out | D2: preserve payload; QR round-trip test with migrated data |
| Double charge on retry/double-click | Financial loss | Idempotency keys, pessimistic lock, disabled buttons, no POST auto-retry |
| Race on simultaneous scans | Negative balance | `SELECT … FOR UPDATE`, `CHECK` constraint, concurrency test |
| Float rounding of money | Balance drift | `BigDecimal`/`DECIMAL`, string-based parsing in migration |
| Django password hashes | Locked-out staff | Legacy encoder with re-hash on login, or forced reset |
| Webcam native library problems | Check-in unusable | Camera selector, fork fallback, manual-entry and keyboard-wedge scanner fallback |
| EDT blocking → frozen UI | Poor UX | `Async` helper, review checklist, smoke tests |
| Time zone mismatches in analytics | Wrong reports | UTC storage, explicit business time zone, golden comparison |
| Swing looks dated | UX perception | FlatLaf, `GlassPanel`, consistent `Theme`, SVG icons |
| Users lack Java 25 | App won't start | Launcher scripts check Java and explain how to install it |

---

## 19. Appendix A — Dependency checklist (artifact coordinates)

| Purpose | Coordinates |
|---|---|
| MySQL driver | `com.mysql:mysql-connector-j` |
| Flyway | `org.flywaydb:flyway-core`, `org.flywaydb:flyway-mysql` |
| JWT | `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` |
| OpenAPI | `org.springdoc:springdoc-openapi-starter-webmvc-ui` |
| MapStruct | `org.mapstruct:mapstruct`, `mapstruct-processor` |
| Testcontainers | `org.testcontainers:mysql`, `junit-jupiter` |
| FlatLaf | `com.formdev:flatlaf`, `flatlaf-extras`, `flatlaf-intellij-themes` |
| MigLayout | `com.miglayout:miglayout-swing` |
| JFreeChart | `org.jfree:jfreechart` |
| ZXing | `com.google.zxing:core`, `com.google.zxing:javase` |
| Webcam | `com.github.sarxos:webcam-capture` |
| SQLite (migration only) | `org.xerial:sqlite-jdbc` |
| CLI | `info.picocli:picocli` |
| Rate limit | `com.bucket4j:bucket4j-core` (or in-memory implementation) |
| Swing tests | `org.assertj:assertj-swing-junit` |
| Architecture tests | `com.tngtech.archunit:archunit-junit5` (optional) |

Verify each coordinate and the latest stable version when writing the POM. If a coordinate has moved, record the replacement in `DECISIONS.md`.

## 20. Appendix B — Useful commands

```bash
# Build everything
cd java && ./mvnw -B clean verify

# Start database
docker compose up -d mysql

# Run server (dev)
./mvnw -pl venuity-server spring-boot:run -Dspring-boot.run.profiles=dev

# Run client (dev)
./mvnw -pl venuity-client exec:java -Dexec.mainClass=com.venuity.client.app.Main

# Migrate data (dry run first)
java -jar venuity-migrate/target/venuity-migrate-*-shaded.jar \
  --sqlite ../backend/db.sqlite3 \
  --mysql-url jdbc:mysql://localhost:3306/venuity --mysql-user venuity \
  --mysql-password-env MYSQL_PASSWORD --dry-run --report migration-report.md

# UI smoke tests headless (Linux CI)
xvfb-run -a ./mvnw -pl venuity-client verify
```

## 21. Appendix C — Progress checklist (update as you go)

- [x] Phase 0 Inventory and golden files
- [x] Phase 1 Skeleton, MySQL, CI
- [x] Phase 2 Schema and Flyway
- [x] Phase 3 Backend and tests
- [x] Phase 4 Migration tool and reconciliation
- [x] Phase 5/6 Web UI amendment (superseded Swing, embedded static SPA)
- [x] Phase 7 Hardening and docs
- [x] Phase 8 JAR packaging (single `venuity.jar` release) and single-command launcher
- [ ] Phase 9 Cutover and cleanup
