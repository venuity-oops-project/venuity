# Architecture Decision Records

ADR format: context → decision → consequences.

## ADR-001 — JDK target and build tool

- **Context:** The original plan specified Java 21. The product owner requested
  Java 25, and the execution machine has Temurin JDK 25.
- **Decision:** Compile and run on Java 25 with Maven Wrapper (`mvnw`/`mvnw.cmd`)
  pinned to Maven 3.9.x. Use JaCoCo 0.8.13, which supports Java 25 bytecode.
- **Consequences:** CI and Docker use Temurin 25. Java 25 is now required to
  build or run the application; no `JAVA_HOME` override is needed on a Java 25
  installation.

## ADR-014 — Single-JAR release and launcher

- **Context:** The supported product is the Spring Boot server with its browser
  UI in `src/main/resources/static`; users should not need to coordinate client
  and server JARs.
- **Decision:** The server artifact is named `venuity.jar` and is the sole
  distributable uploaded by CI. `java/run.ps1` starts MySQL, rebuilds this JAR
  when necessary, and launches it with one command.
- **Consequences:** The migrator and retained Swing module remain development
  tools, not release artifacts. MySQL is still required because it is the
  production database and is intentionally not embedded in the JAR.

## ADR-002 — Preserve the legacy QR payload (D2)

- **Context:** The Django `Customer.qr_id` is a UUID. `_generate_qr_code` calls
  `str(parsed_qr_id)`, producing canonical 36-character lower-case dashed UUID
  text with QR error correction H, box 10, border 4. The pre-Django Flask ids
  were 32-character hex strings, and `docs/BACKEND.md` states those ids were
  carried into `qr_id`.
- **Decision:** The new server stores a `public_code` string containing the
  exact scanned/printed QR text. New customers receive a canonical dashed UUID
  string (matching the current Django generator). Lookups accept both the
  canonical dashed form and the legacy 32-hex form, case-insensitively, so
  already-printed passes keep working.
- **Consequences:** `public_code` is a string, not a UUID column. QR images are
  generated with ZXing at error-correction level H-equivalent (see ADR-005) and
  the same payload text. Migration preserves the legacy QR payload. SQLite
  stores UUID fields as raw 32-hex, so Phase 4 canonicalizes those to the dashed
  UUID Django actually prints/serializes (D-26, D-28); runtime lookup still
  accepts both forms.

## ADR-003 — Auth semantics (D3)

- **Context:** The source has JWT auth (SimpleJWT) with ADMIN
  (`is_superuser`) and STAFF (`is_staff`) roles. Access 8 h, refresh 7 d.
- **Decision:** Keep JWT HS256 + BCrypt. Preserve ADMIN/STAFF semantics:
  STAFF may read and perform check-in/recharge; ADMIN additionally edits
  settings, deletes/deactivates customers, and adjusts/refunds.
  Access lifetime defaults to 8 h (source), refresh 7 d. Refresh rotation is
  supported. Login rate limiting (5 failures/min/IP+username) is an intentional
  security addition.
- **Consequences:** Existing Django users can be migrated with a legacy
  PBKDF2 encoder and re-hashed to BCrypt on first login; otherwise an admin
  reset path is documented in `QUESTIONS.md`.

## ADR-004 — Soft delete vs the legacy hard delete

- **Context:** The source hard-deletes customers and cascades their ledger.
  Plan default D7 requires soft delete because ledger rows must not be lost.
- **Decision:** Follow D7: deactivate (`active=false`). No hard delete of a
  customer with ledger rows. A zero-ledger customer may also simply be
  deactivated; the API's `DELETE` returns 204 and is ADMIN-only.
- **Consequences:** A `CUSTOMER_INACTIVE` check applies to money operations.
  This is an intentional, documented deviation from source behaviour.

## ADR-005 — QR generation library and payload fidelity

- **Context:** Source uses Python `qrcode` with error correction H, box 10,
  border 4. Plan suggests ZXing error correction M, margin 2. Payload fidelity
  matters more than module size.
- **Decision:** Use ZXing with error correction level H and margin 4 for
  maximum compatibility with printed legacy passes; PNG size is request-driven.
  Round-trip tests assert the decoded text equals the stored `public_code`.
- **Consequences:** Slightly denser images than M but better damage tolerance.
  Documented deviation from plan §9.7.

## ADR-006 — Keep the legacy analytics definitions

- **Context:** Source "revenue" means top-ups (`SUM(recharge.amount)`), while
  "checkins" is a count of check-in transactions. Check-in `amount` is positive
  and is not summed as revenue. The UI offers "Customers" (sends `customers`)
  and "Revenue", but the backend only knows `checkins` and `revenue`; sending
  `customers` silently becomes `revenue`. It also offers "Daily" (sends
  `daily`) although the backend only knows `weekly`/`monthly`/`hourly`.
- **Decision:** Implement both contracts on the new server. Primary endpoints
  (`/api/analytics/summary`, `/daily`, `/hourly`) follow the plan, but a
  compatibility endpoint also serves the legacy shape. `metric=customers` is
  treated as `checkins`, and `interval=daily` as a day series. This removes the
  source UI bugs while preserving old API behaviour.
- **Consequences:** Analytics totals match the old system for old callers.
  `DISCREPANCIES.md` records the old UI bug.

## ADR-007 — Money representation

- **Context:** Source uses DECIMAL(10,2) and Python `Decimal`. The plan forbids
  floating point for money.
- **Decision:** Java `BigDecimal` scale 2, HALF_UP, normalised in one utility;
  MySQL `DECIMAL(12,2)`. Ledger amounts are signed in the new system (credit
  positive, debit negative) while balances remain non-negative.
- **Consequences:** Ledger invariant `balance = SUM(amount)` holds directly.
  Migration converts legacy positive check-in amounts to negative ledger rows.

## ADR-008 — Time zone

- **Context:** Source stores UTC (`USE_TZ=True`) and buckets analytics in
  `Asia/Kolkata`. The plan suggests adding `business_timezone`.
- **Decision:** Store `Instant`/`TIMESTAMP` in UTC; bucket by a configurable
  `business_timezone` defaulting to `Asia/Kolkata`. Display on the client in
  local time.
- **Consequences:** Existing analytics totals are preserved when the setting is
  left at its default.

## ADR-009 — Verified dependency baseline

- **Context:** Plan asks for latest stable versions but the environment must
  build reproducibly.
- **Decision:** Spring Boot 3.5.16, Spring Framework 6.2.x, Hibernate 6.6.x,
  MySQL Connector/J 9.7.0, Flyway 11.7.2 (with `flyway-mysql`), JJWT 0.12.7,
  springdoc 2.8.17, Jackson 2.21.7, Tomcat 10.1.59, Log4j 2.25.5, FlatLaf 3.5.1,
  MigLayout 11.4.2, JFreeChart 1.5.5, ZXing 3.5.3, SQLite JDBC 3.53.4.0,
  picocli 4.7.6, Testcontainers 2.0.5, docker-java 3.7.1, JaCoCo 0.8.12.
  These are stable releases available on Maven Central. Jackson, Tomcat and
  Log4j are overridden through imported BOMs/direct `dependencyManagement`
  entries because the Spring Boot BOM still selects versions with known
  vulnerabilities.
- **Consequences:** Pinned in the parent POM; upgrades are deliberate.
  Testcontainers 2.0.5 requires commons-compress 1.28.0, commons-lang3 3.18.0
  and commons-io 2.20.0; these are pinned in `dependencyManagement` because
  the Spring Boot BOM otherwise selects older versions that fail at runtime
  (`NoSuchMethodError`). The dependency scan and its two accepted Spring
  Framework advisories are recorded in `DEPENDENCY_SCAN.md`.

## ADR-010 — Camera and scanner fallback

- **Context:** `webcam-capture` native binaries can fail on some OSes.
- **Decision:** Start with `com.github.sarxos:webcam-capture:0.3.12`; abstract
  capture behind a `FrameSource` interface so a fork can be swapped and so
  tests can use static images. Always offer manual/keyboard-wedge entry.
- **Consequences:** Check-in remains usable without a camera.

## ADR-011 — Real data found in the repository

- **Context:** `backend/scripts/legacy_data_export.json` (5.3 MB) contains 26
  named customers, 27,500 transactions and settings.
- **Decision:** Do not copy, transform, or commit this data anywhere else. Use
  synthetic fixtures only. Recorded in `QUESTIONS.md` for the owner to decide
  whether the file should be removed from history.
- **Consequences:** Phase 4 migration is validated against synthetic data until
  the owner provides an out-of-band database dump.

## ADR-012 — Phase 4 migration semantics

- **Context:** Django runs with `USE_TZ=True` and stores UTC despite the plan
  suggesting the configured `TIME_ZONE`. SQLite stores UUID fields as raw
  32-hex, while Django's API and QR payloads serialize dashed UUIDs. The
  synthetic fixture also contains historical check-ins that make an intermediate
  running total negative, which the target `balance_after >= 0` CHECK forbids.
- **Decision:** Interpret naive source timestamps as UTC by default, with
  `--source-timezone` available for an exceptional source. Canonicalize raw
  32-hex UUIDs to dashed UUIDs when writing user/customer public IDs and QR
  codes; the QR payload is thereby preserved and runtime lookup still accepts
  both forms. Copy Django `pbkdf2_sha256$...` hashes verbatim; the existing
  `PasswordService` verifies them and `AuthService` re-hashes to BCrypt on
  successful login. Write all tables in one transaction with 500-row batches.
  Preserve signed ledger amounts and final balances; when a historical running
  total is negative, clamp only the stored `balance_after` to `0.00` and report
  each clamped customer. Insert `OPENING_BALANCE` for history-free positive
  balances and one `ADJUSTMENT` when the imported ledger differs from the old
  balance.
- **Consequences:** Migration preserves every wallet total and the printable QR
  payload. Historical intermediate balances that were negative in the source
  are displayed as zero, with an explicit report warning. `--dry-run` performs
  the full reconciliation and rolls back. A reconciliation failure returns a
  non-zero exit code and, when `--report` is supplied, writes a failed report.
  With `--force-empty-check-off`, existing target rows (including idempotency
  records) are deleted inside the same transaction before import; a dry run
  rolls back both the deletion and the writes.

## ADR-013 - Static web client replaces the Swing UI

- **Context:** The migration plan required a Java Swing desktop client (C1/C2).
  The product owner subsequently requested the browser-based UI that was already
  prepared under `java/venuity-server/src/main/resources/static/` instead of
  completing the Swing screens.
- **Decision:** Serve a plain HTML/CSS/JavaScript single-page client from Spring
  Boot static resources. It uses the modern camelCase `/api` contract, Chart.js
  and jsQR vendor bundles, `localStorage` for tokens/settings/theme, and the
  same glassmorphism design as the React frontend. No npm build step is added.
  The Swing module remains in the tree but is superseded until Phase 9 cleanup.
- **Consequences:** C1/C2 are explicitly overridden; JavaScript and CSS are part
  of the new application. Camera scanning requires HTTPS or localhost. The QR
  PNG endpoint is authenticated, so the client fetches it as a blob rather than
  using a bare `<img src>`. UI verification is browser/manual rather than
  AssertJ-Swing; static syntax checks and the Java build remain in CI. Release
  packaging ships the server JAR and its static UI; the Swing client JAR is not
  a release artifact.
