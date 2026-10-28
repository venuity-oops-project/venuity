# Progress log

Append one entry per completed task: date, task id, change, tests run, result.

| Date | Task | Change | Tests | Result |
|---|---|---|---|---|
| 2026-10-26 | P0-1/P0-2/P0-3/P0-4 | Read backend, frontend, Tauri, docs, scripts; wrote `INVENTORY.md` | code review only | done |
| 2026-10-26 | P0-5 | Stood up the source backend in a throwaway Python venv against a throwaway synthetic SQLite DB; no production data used | `manage.py check`, `manage.py migrate`, test-client requests | done |
| 2026-10-26 | P0-6 | Captured 42 golden request/response pairs covering every endpoint (success and error cases), including a QR PNG; credentials redacted | `capture_golden.py` runs clean; 42 JSON + 1 PNG | done |
| 2026-10-26 | P0-7 | Built synthetic `golden/sample.sqlite3`: 12 customers (zero/tiny/large/unicode balances), 26 ledger rows, 3 users, settings | seed asserts 0 ledger mismatches; balance total 1,346,749.99 | done |
| 2026-10-26 | P0-8 | Wrote `DECISIONS.md` (ADR-001..011), `DISCREPANCIES.md` (D-01..D-25), `QUESTIONS.md` (Q-01..Q-04) | review | done |
| 2026-10-26 | P1 | Created parent/module Maven build, Maven Wrapper, Spring configs, security skeleton, Swing/migrator entry points, Docker Compose and CI workflow | `mvnw -B -ntp clean verify` (JDK 21) | done; green |
| 2026-10-26 | P1 | Started MySQL 8.4 Compose service; confirmed server starts and `/actuator/health` is `UP` | server smoke run | done |
| 2026-10-26 | P2 | Added Flyway `V1__init.sql` and `V2__seed_settings.sql` | served by `SchemaConstraintsTest` | done |
| 2026-10-26 | P2 | Added Testcontainers schema test for seed, negative balance and single settings row; fixed Testcontainers 2.0.5 / docker-java 3.7.1 / Commons dependency alignment | `mvnw -pl venuity-server -am test -Dtest=SchemaConstraintsTest` | 3 tests pass |
| 2026-10-26 | P1/P2 | Ran full build and applied V1+V2 to the local Compose MySQL; verified Flyway history and health | `mvnw -B -ntp clean verify`; server smoke run | BUILD SUCCESS; health UP; V1/V2 applied |
| 2026-10-26 | P3 | Added entities/services/security and modern plus `/api/v1` legacy controllers; aligned entities to Flyway `CHAR`/`TINYINT`/`TIMESTAMP(3)` columns | `mvnw -B -ntp test-compile`; local endpoint smoke requests | compiles; JPA validation passes |
| 2026-10-26 | P3 | Added `GoldenFixtureLoader` and stateful `GoldenContractTest` replaying all 42 JSON cases against Testcontainers MySQL; normalized volatile ids/timestamps/links, decoded QR PNG payload, and made recent-transaction ordering deterministic | `mvnw -B -ntp test` (JDK 21) | 4 tests pass; golden replay green |
| 2026-10-26 | P0-8 | Documented D-26/D-27 for fixture QR id canonicalization and QR PNG byte differences | review | done |
| 2026-10-26 | P3 | Added `WalletFlowIntegrationTest`: idempotent recharge replay, randomized ledger invariant, 50-thread concurrent check-in (10 successes/40 insufficient), and invalid wallet-operation cases; moved wallet flows to ID projection lookups before row locks | `mvnw -B -ntp test` (JDK 21) | 9 tests pass; BUILD SUCCESS |
| 2026-10-26 | P3 | Added OpenAPI smoke assertions and an enforcing JaCoCo check for `WalletService`/`CheckInService` (>=85% line, measured 96.2%/100%) | `mvnw -B -ntp verify` (JDK 21) | BUILD SUCCESS; coverage checks met |
| 2026-10-26 | P4 | Implemented `venuity-migrate` CLI and pipeline: read-only SQLite reader, target preflight, settings/user/customer import, ledger backfill with opening/adjustment rows, reconciliation, Markdown report, `--dry-run` rollback | `mvnw -pl venuity-migrate -am test -Dtest=MigrationIntegrationTest` (JDK 21) | 4 tests pass; sample total 1,346,749.99 |
| 2026-10-26 | P4 | Added Testcontainers coverage for sample DB, non-empty-target refusal, dry-run rollback, fractional rounding, huge balance, unicode/duplicate names, NULL phone, and failed-report output | same command | BUILD SUCCESS |
| 2026-10-26 | P4 | Verified legacy PBKDF2 login path and BCrypt rehash against the migrated fixture; documented UTC, UUID canonicalization, negative `balance_after`, and transaction-scope decisions | `GoldenContractTest`; docs review | login/rehash assertions pass |
| 2026-10-26 | P4 | Made `--force-empty-check-off` transactionally delete and replace existing target data, including idempotency rows; a forced dry run rolls back the deletion and writes; added integration coverage and ran the full build | `MigrationIntegrationTest`; `mvnw -B -ntp verify` | 5 migration tests pass; BUILD SUCCESS |

## Verification Gate 0

- [x] `INVENTORY.md` covers models, endpoints, screens, auth, QR format, business rules.
- [x] Golden files exist for every endpoint (42 cases in `docs/migration/golden/`).
- [x] Synthetic `sample.sqlite3` exists with varied data (the repo's real-looking `legacy_data_export.json` was not used).
- [x] `DECISIONS.md` resolves D2 (QR format) and D3 (auth) from evidence.

## Verification Gate 1

- [x] Maven wrapper builds all modules on JDK 21.
- [x] All modules compile and the three runnable/fat JARs are produced.
- [x] Server starts against MySQL 8.4 and actuator health is `UP`.
- [x] CI workflow is present; it has not run on a remote runner yet.
- [x] Flyway applies V1 on a fresh database.

## Verification Gate 2

- [x] Flyway applies cleanly on an empty MySQL 8.4.
- [x] Testcontainers test confirms the `CHECK` constraints reject a negative
      balance and a second settings row.
- [x] `ddl-auto=validate` passes against Phase 3 entities.

## Verification Gate 3

- [x] All endpoints implemented; OpenAPI UI loads at `/swagger-ui.html`.
- [x] Contract tests pass for all golden files (deviations documented in `DISCREPANCIES.md`).
- [x] Concurrency, invariant and idempotency tests pass.
- [x] `./mvnw -B verify` is green, and test coverage for `wallet` and `checkin` packages is >= 85% line coverage (JaCoCo).

## Verification Gate 4

- [x] Migration of `golden/sample.sqlite3` reconciles with zero row, balance, and ledger differences.
- [x] Re-running against a non-empty target is blocked by preflight.
- [x] Migrated users can log in with legacy PBKDF2 hashes; successful login rehashes them to BCrypt.
- [x] `--force-empty-check-off` replaces existing target data transactionally; a forced dry run rolls back both deletion and writes.

## Web UI tasks (plan amendment)

| Date | Task | Change | Tests | Result |
|---|---|---|---|---|
| 2026-10-28 | P5-web | Added static SPA foundations: `api.js`, `store.js`, `ui.js`, `scanner.js`, plus Dashboard, Customers and Recharge pages | Node module syntax check; `mvnw -pl venuity-server -am -DskipTests package` | syntax OK; BUILD SUCCESS |
| 2026-10-28 | P5-web | Added Check-in, Analytics and Settings pages, app bootstrap/hash router/shell/login/home, vendor script loading and CSS utilities | Node module syntax check; Maven package | syntax OK; BUILD SUCCESS |
| 2026-10-28 | P3 | Added ADMIN-only `POST /api/customers/{publicId}/reactivate` endpoint and service method | Maven package | BUILD SUCCESS |
| 2026-10-28 | P5-web | Recorded ADR-013 (static web client replaces Swing) and plan amendment; documented verification status | docs review | done |
| 2026-10-28 | P5-web | Headless Chrome smoke test against a temporary mock API | CDP script: 31/31 checks | PASS |
| 2026-10-28 | P5-web | Headless Chrome smoke test against the real Spring Boot server and MySQL | CDP script: 31/31 checks | PASS |
| 2026-10-28 | P5-web | Full Maven verification with Docker/Testcontainers available | `mvnw -B verify` (server 9 tests, migrator 5 tests) | BUILD SUCCESS |
| 2026-10-28 | P7 | Added `java/README.md`, architecture, operations and QA checklist; added `application-prod.yml` graceful shutdown; added static JS syntax check to CI | docs review; Node syntax check; `docker compose config`; full `mvnw -B verify` | done; BUILD SUCCESS |
| 2026-10-28 | P8 | Added server Dockerfile, `.dockerignore`, and optional Compose `server` profile | Docker image build; container health check; real browser smoke 31/31 | PASS |
| 2026-10-28 | P8 | Added Dependabot configuration for Maven and GitHub Actions | config review; OSV scan | added; scan completed in `DEPENDENCY_SCAN.md` |
| 2026-10-28 | P8 | Added opt-in `CheckInLoadTest`; raised Hikari default to 20 for 20 concurrent users; documented single-customer row-lock serialization | `mvnw -pl venuity-server -am -Dtest=CheckInLoadTest -DloadTest=true -Djacoco.skip=true test` (JDK 21) | many-customer p95 169 ms; single-customer p95 776 ms; no deadlocks; PASS |
| 2026-10-28 | P8 | Upgraded the dependency baseline to Spring Boot 3.5.16, Flyway 11.7.2, MySQL Connector/J 9.7.0, springdoc 2.8.17, Jackson 2.21.7, Tomcat 10.1.59 and Log4j 2.25.5; completed an OSV scan of all resolved Maven dependencies | `mvnw -B verify` (JDK 21); OSV querybatch over 148 artifacts | BUILD SUCCESS; no reachable high/critical issues; two non-reachable Spring Framework 6.2 advisories accepted in `DEPENDENCY_SCAN.md` |
| 2026-10-28 | P8 | Re-ran the real full-stack browser smoke test after the dependency upgrade | CDP script: 31/31 checks | PASS |
| 2026-10-28 | P8 | Clean-copy walkthrough of the working tree (excluding `target/` and `.env`) with repo docs present | `mvnw -B verify` (JDK 21) | BUILD SUCCESS; server and migrator tests pass |
| 2026-10-28 | P8 | Upgraded build, CI and Docker to Java 25; named the supported Spring Boot release `venuity.jar`; added `java/run.ps1` to start MySQL, package and run the app in one command | Server packaging passed; JAR manifest and Java 25 class version verified; full Testcontainers suite blocked by Docker named-pipe access | packaging complete; full integration rerun pending Docker access |
| 2026-10-28 | P8 | Updated `VENUITY_JAVA_MIGRATION_PLAN.md` to Java 25 LTS and single-JAR specifications; created `PROJECT_BRIEF.MD`; developed multi-platform single-command launchers at repo root (`run.ps1`, `run.bat`, `run.sh`, `npm start`); verified `venuity.jar` execution and health on Java 25 | `run.ps1` single-command execution; `curl /api/health` returned UP; root `/` served static SPA | PASS |

## Verification Gate 5/6 (web UI amendment)

- [x] Every static JavaScript module parses with Node's module syntax checker.
- [x] `mvnw -B -pl venuity-server -am -DskipTests package` succeeds on JDK 21.
- [x] React page behaviour, API shapes, CSS class contract and missing DTO fields were checked against source/backend.
- [x] Headless Chrome smoke test against a temporary mock API: login, home, dashboard, analytics/Chart.js, settings, recharge confirmation/receipt, manual check-in, customers/QR modal, logout, no page errors.
- [x] Full-stack headless Chrome smoke test against the real Spring Boot server and MySQL: same 31 checks passed.
- [x] `mvnw -B verify` with Docker/Testcontainers available: server 9 tests (the opt-in load test is skipped) and migrator 5 tests pass; the load test passes separately.
- [x] Container packaging: Dockerfile builds, the Compose `server` profile starts healthy, and the browser smoke passes against it.
- [ ] Camera scanning path exercised with a physical camera (needs hardware/permission).

## Phase 7/8 status (partial)

- [x] README, architecture, operations and QA documentation added.
- [x] Static JavaScript syntax check added to CI.
- [x] Production profile enables graceful shutdown and INFO logging.
- [x] Server container image and Compose app profile added and verified.
- [ ] CI workflow has run on a remote GitHub runner.
- [x] Dependency-vulnerability scan completed; two non-reachable Spring Framework 6.2 advisories are accepted in `DEPENDENCY_SCAN.md`.
- [x] Load test completed with a saved report; single-customer serialization is documented as an accepted exception.
- [x] Clean-copy build walkthrough passed (working tree minus `target/` and `.env`, with `docs/` present).
- [ ] Fresh-clone README walkthrough recorded; the changes must be committed and pushed before a true clone can be exercised.
