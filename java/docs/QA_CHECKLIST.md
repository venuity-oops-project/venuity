# QA Checklist

Use this checklist before a release or cutover. Record the date, environment and
evidence for each item.

## Automated checks

- [ ] `.\mvnw.cmd -B verify` succeeds on JDK 25 with Docker running.
- [x] Server tests cover golden API contracts, schema constraints, wallet
      idempotency, concurrency, ledger invariants and insufficient balance.
- [x] Migration tests cover the synthetic SQLite fixture, non-empty-target
      refusal, dry-run rollback, rounding, unicode names and report output.
- [x] Every static JavaScript module passes
      `node --input-type=module --check`.
- [x] Headless Chrome smoke test against the mock API: 31/31 checks.
- [x] Headless Chrome smoke test against the real server and MySQL: 31/31 checks.
- [x] JaCoCo line coverage for wallet and check-in code is at least 85%.
- [ ] CI has run successfully on a remote GitHub runner.
- [x] Dependency-vulnerability scan completed. No reachable high/critical
      issues; two non-reachable Spring Framework 6.2 advisories are accepted in
      `docs/migration/DEPENDENCY_SCAN.md`.
- [x] Load test at 20 concurrent check-ins shows no deadlocks. Many-customer
      p95 is 169 ms; single-customer p95 is 776 ms because one row is
      intentionally serialized. See `docs/migration/LOAD_TEST_REPORT.md`.

## Browser smoke test coverage

- [x] Login page renders and branding loads.
- [x] Admin login reaches Home.
- [x] Dashboard stats and recent activity render.
- [x] Analytics summary and Chart.js chart render.
- [x] Settings load and preview reflects the configured fee.
- [x] Recharge search, selection, confirmation and receipt.
- [x] Manual check-in succeeds and appears in recent check-ins.
- [x] Customers list, QR modal and authenticated blob image load.
- [x] Home shows all six module cards.
- [x] Logout clears tokens and returns to login.
- [x] No page JavaScript errors during the run.

## Manual checks

- [ ] Login with an incorrect password shows a useful error and does not leak
      whether the username exists.
- [ ] Recharge with an invalid amount is rejected; the receipt matches the
      entered amount.
- [ ] Check-in with insufficient balance shows the required/available message.
- [ ] A replayed recharge or check-in with the same `Idempotency-Key` does not
      move money twice.
- [ ] An inactive customer cannot be recharged or checked in.
- [ ] ADMIN can edit settings and deactivate/reactivate a customer; STAFF cannot.
- [ ] QR modal image loads after a fresh login and after an access-token refresh.
- [ ] Refresh-token expiry or logout returns the user to login cleanly.
- [ ] Physical camera scanning works over HTTPS or localhost with permission
      granted.
- [ ] Printed QR passes from the legacy system resolve correctly.

## Data and cutover checks

- [ ] Synthetic fixture migration reports zero row, balance and ledger
      differences.
- [ ] A copy of production data is migrated and reconciled before cutover.
- [ ] Customer count, total balance, ledger count and check-in count match the
      source for the same cutoff.
- [ ] Final SQLite and MySQL backups exist and a restore has been tested.
- [ ] Old system remains read-only for the agreed rollback window.

## Known gaps

- Physical camera scanning has not been exercised with hardware.
- User administration beyond the bootstrap ADMIN is not implemented in the UI.
- The refresh-token blacklist is in memory and is lost on restart.
- Two non-reachable Spring Framework 6.2 advisories are accepted in
  `docs/migration/DEPENDENCY_SCAN.md`; reassess them when upgrading to Spring
  Boot 4.x.
- The load-test exception is documented in `docs/migration/LOAD_TEST_REPORT.md`.
- A clean-copy Maven build passed; a true fresh-clone README walkthrough on a
  clean machine still needs the changes committed and pushed.
