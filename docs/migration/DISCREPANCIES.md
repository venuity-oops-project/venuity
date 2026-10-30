# Source vs. migration-plan discrepancies

The source code wins; each item below is reflected in the target design or
flagged for the owner.

| # | Area | Plan says | Source actually does | Resolution |
|---|---|---|---|---|
| D-01 | API prefix | `/api/` | `/api/v1/` | New server serves the plan's clean `/api/...` routes and a `/api/v1/...` compatibility alias for legacy clients. |
| D-02 | Money sign | Ledger `amount` signed (debit negative) | Both recharge and check-in `amount` are positive; direction comes from `type` | New ledger is signed (ADR-007). Migration negates check-in rows. Legacy API compatibility responses keep positive amounts. |
| D-03 | Insufficient balance | Error code mapped to 422 (or 409) | HTTP **402 Payment Required** with `{"error":"Insufficient balance. Required: X, Available: Y"}` | New API returns 409/422 with `INSUFFICIENT_BALANCE`; compatibility layer returns 402 and the legacy message. |
| D-04 | Delete customer | Soft delete (D7) | Hard delete, cascades ledger | Soft delete per D7 (ADR-004). |
| D-05 | Customer fields | phone, active, version, updated_at | Only name, balance, qr_id, public_id, created_at | Add phone/active/version/updated_at as intentional additions; migration fills null/defaults. |
| D-06 | Auth | Plan proposes `/auth/login` returning `accessToken` | SimpleJWT `/auth/token/` returns `access`, `refresh`, `user`; no `/health` | New `/api/auth/login` clean shape plus legacy token endpoints. Health endpoint added. |
| D-07 | QR image | ZXing EC level M, margin 2 | Python qrcode EC **H**, box 10, border 4 | Use EC level H, margin 4 (ADR-005). |
| D-08 | Analytics | `/analytics/summary`, `/daily`, `/hourly` with from/to ranges | Single `/analytics/?interval=hourly|weekly|monthly&metric=revenue|checkins&date=`; fixed business hours 9–20; week starts Monday | Implement both. Legacy shape preserved. |
| D-09 | Revenue definition | Plan is ambiguous | "revenue" = SUM(recharge.amount), not check-in fees | Keep source definition (ADR-006). |
| D-10 | Analytics UI metric | — | UI sends `metric=customers`; backend coerces unknown metrics to `revenue`, so the "Customers" toggle shows revenue | New compatibility layer maps `customers`→`checkins`; documented as a bug fix, not behavioural parity. |
| D-11 | Analytics UI interval | — | UI sends `daily`; backend falls back to `hourly` | New compatibility layer maps `daily`→day series. |
| D-12 | Analytics response fields | `from`/`to` and zero-filled series | Returns `labels`, `data`, `display_date`, `interval`, `metric`; no `start`/`end` | UI reads `data.start`/`data.end` (undefined), so its date label shows "Invalid Date"/similar. New API returns both legacy and modern fields. |
| D-13 | Dashboard revenue label | — | UI labels SUM(recharge) as "Checkin Revenue" | Keep calculation; label clarified in new UI as "Recharge revenue". |
| D-14 | Duplicate scan rule | Apply old rule if one exists | None server-side; ~2 s client pause only | Implement client-side 3 s debounce per plan; server accepts every request. |
| D-15 | Customer edit | GET/PUT | No PUT/update endpoint at all | Add `PUT/PATCH` as an intentional feature; document in DECISIONS. |
| D-16 | Health | `/api/health` public | No health endpoint | Add. |
| D-17 | User roles | ADMIN/STAFF enum | Booleans `is_superuser`/`is_staff`; `is_staff=False` users cannot access API | Map superuser→ADMIN, staff→STAFF, both→ADMIN. Inactive→disabled. |
| D-18 | Password storage | BCrypt | Django PBKDF2 `pbkdf2_sha256$...` | Legacy encoder + rehash on login (Phase 4). |
| D-19 | QR payload | Determine from Phase 0 | Canonical dashed UUID text; legacy 32-hex ids also seen | Accept both; store exact text (ADR-002). |
| D-20 | `total_revenue` docstring | — | Mentions `next_offset`, never returned | New stats endpoint omits it too. |
| D-21 | Customer search | name/phone/code | Name contains only | New endpoint searches name/phone/code; compatibility `?search=` stays name-only by default. |
| D-22 | Old data in repo | Do not copy real data | `legacy_data_export.json` contains real-looking customers | Do not use; recorded in QUESTIONS.md. |
| D-23 | Settings storage | Single row | Key/value table with seven keys | New table holds the seven columns (single row) plus a key/value compatibility view/service; legacy GET/POST shape preserved. |
| D-24 | Recharge unknown customer | 404 `NOT_FOUND` | Uncaught `Customer.DoesNotExist` escapes the view and becomes HTTP **500** `server_error` (confirmed by golden). Check-in handles the same case as 404. | New server returns 404 `NOT_FOUND`; compatibility mode matches 404, not the source crash. |
| D-25 | Unknown route | JSON error envelope | Django debug 404 HTML page (development server). | Not part of the API contract; new server returns the JSON error envelope. |
| D-26 | Golden fixture QR identifiers | QR payload is the canonical dashed UUID (D-19). | `sample.sqlite3` stores some `qr_id`/`public_id` values as raw 32-hex, while the captured API and QR PNG use the dashed UUID serialized by Django. | `GoldenFixtureLoader` canonicalizes fixture identifiers to dashed UUIDs. Runtime lookup accepts both forms, so legacy 32-hex passes still work. |
| D-27 | QR PNG bytes | Byte-for-byte QR image compatibility is not required. | Python `qrcode` and ZXing produce different PNG dimensions/bytes for the same payload. | `GoldenContractTest` decodes the target PNG and compares the QR text, not the image hash. Image size remains request-driven (ADR-005). |
| D-28 | Migration timestamps | Plan: use Django `TIME_ZONE` for naive datetimes. | `USE_TZ=True`; SQLite timestamps are naive UTC (`2026-04-01 04:30:00` is 10:00 IST). | Phase 4 uses UTC by default and exposes `--source-timezone` for exceptional sources (ADR-012). |
| D-29 | Migration UUID text | ADR-002 says preserve legacy QR identifiers. | SQLite stores raw 32-hex UUIDs, while the printed/serialized legacy value is the canonical dashed UUID. | Phase 4 canonicalizes raw 32-hex IDs to dashed UUIDs; runtime lookup accepts both, so printed passes still work (ADR-012). |
| D-30 | Migration intermediate balances | Recompute `balance_after` as the exact running total. | The synthetic source has opening check-ins that make an intermediate running total negative; target `balance_after` has a nonnegative CHECK. | Preserve signed amounts and final balance; clamp only historical `balance_after` to `0.00`, and list each clamped customer in the report (ADR-012). |
| D-31 | Migration transaction scope | One MySQL transaction per table. | Independent table commits would leave a failed migration partially applied. | Phase 4 uses one all-table transaction (still 500-row batches) and rolls back on any failure, which is stricter and safer (ADR-012). |
| D-32 | User Management Location | User creation modal placed inside Settings page header and sidebar | Settings page overcrowded; users needed dedicated administration view | Removed 'Add user' from Settings; created top-level Users section (#/users) with directory, drawer, and audit tab. |
| D-33 | Bootstrap Admin Reset | BootstrapAdminRunner unconditionally overwrote credentials on every startup | Prevents persistent password changes by administrators in production | Controlled via BOOTSTRAP_ADMIN_FORCE_RESET (default alse in prod, 	rue in dev). |
| D-34 | Token Invalidation & Blacklist | In-memory blacklist lost on server restart | Does not survive container redeployments or cluster environments | Backed by persistent user_session table (SHA-256 token hash) and 	oken_version column on pp_user. |
| D-35 | User Unique Email Constraint | email NOT NULL DEFAULT '' with unique constraint uq_app_user_email | Multiple users with empty emails fail MySQL unique constraint | Migration V4__user_management.sql alters email to nullable and converts empty strings to NULL. |
