# Venuity Source System Inventory (Phase 0)

Captured by reading the repository at commit `ebb27f0` on 2026-10-26.
The Python/Django backend is treated as the behavioural source of truth. The React
frontend is treated as the UI contract. Where the migration plan differs from the
code, see `DISCREPANCIES.md`.

Base API prefix in the source system: **`/api/v1/`** (not `/api/` as the plan's
Section 2 summary assumed).

---

## 1. Models

### 1.1 `users.User` (`apps.users.models.User`)

Extends `django.contrib.auth.models.AbstractUser`.

| Field | Type | Null | Default | Notes |
|---|---|---|---|---|
| `id` | BigAutoField | no | auto | internal PK, never exposed |
| `password` | varchar(128) | no | — | Django PBKDF2 (`pbkdf2_sha256$...`) |
| `last_login` | datetime | yes | null | |
| `is_superuser` | boolean | no | false | ADMIN role |
| `username` | varchar(150) | no | — | unique, Unicode username validator |
| `first_name` | varchar(150) | no | `''` | |
| `last_name` | varchar(150) | no | `''` | |
| `email` | varchar(254) | no | `''` | |
| `is_staff` | boolean | no | false | STAFF operator role |
| `is_active` | boolean | no | true | |
| `date_joined` | datetime | no | now | |
| `public_id` | UUID | no | uuid4 | unique, indexed, API-facing id |
| groups / user_permissions | M2M | — | — | Django auth scaffolding, unused by API |

Role mapping: `is_superuser=True` → ADMIN; `is_staff=True` → STAFF;
`is_staff=False` → no API access. `Meta.ordering = ['username']`.

### 1.2 `customers.Customer`

| Field | Type | Null | Default | Notes |
|---|---|---|---|---|
| `id` | BigAutoField | no | auto | internal PK, not exposed |
| `public_id` | UUID | no | uuid4 | unique, indexed, API-facing id |
| `name` | varchar(150) | no | — | |
| `balance` | DECIMAL(10,2) | no | `0.00` | money; source uses DecimalField |
| `qr_id` | UUID | no | uuid4 | unique, indexed; value encoded in physical QR |
| `created_at` | datetime | no | now | indexed |

`Meta.ordering = ['-created_at']`; index on `name` (`ix_customer_name`).
No `active`, `phone`, `updated_at`, or version field. Delete is a hard delete
(cascade removes transactions).

### 1.3 `transactions.Transaction`

| Field | Type | Null | Default | Notes |
|---|---|---|---|---|
| `id` | BigAutoField | no | auto | |
| `customer_id` | FK → customer.id | no | — | `on_delete=CASCADE`, related_name `transactions` |
| `transaction_type` | varchar(20) | no | — | choices `recharge`, `checkin`; indexed |
| `amount` | DECIMAL(10,2) | no | `0.00` | **always positive**; type determines direction |
| `notes` | text | no | `''` | |
| `created_at` | datetime | no | now | indexed |

`Meta.ordering = ['-created_at']`; composite index (`transaction_type`,
`created_at`) and index (`created_at`). This is the ledger. Balance is a cached
denormalisation of `SUM(recharge.amount) - SUM(checkin.amount)`.

### 1.4 `settings_app.ApplicationSetting`

Key/value store. `key` is a varchar(100) primary key; `value` is text.
All values are strings cast in the service layer.

Known keys and defaults (`services.DEFAULTS`):

| Key | Default |
|---|---|
| `business_name` | `PlayArea Manager` |
| `currency_symbol` | `₹` |
| `checkin_fee` | `100.00` |
| `primary_color` | `#6366f1` |
| `primary_hover_color` | `#4f46e5` |
| `logo_url` | `/logo.png` |
| `app_title` | `Venuity` |

`business_name`, `primary_color`, `primary_hover_color`, `logo_url`,
`app_title` are also exposed unauthenticated as "branding".

---

## 2. Endpoints

All paths are prefixed with `/api/v1`. Unless stated, auth is
`Authorization: Bearer <access token>`. The error envelope is
`{"error": "...", "code": "...", "detail": {...}}` (code/detail optional).
DRF validation failures use `{"error":"Validation failed.","code":"validation_error","detail":{...}}`.

Pagination envelope: `{count, total_pages, next, previous, results}`.
Default page size 10, query params `page`, `page_size` (max 100).
Customers are ordered newest first.

| Method | Path | Auth | Request | Response / status | Rules & side effects |
|---|---|---|---|---|---|
| POST | `/auth/token/` | public | `{username,password}` | 200 `{access, refresh, user}`; 401 invalid | SimpleJWT, updates last_login |
| POST | `/auth/token/refresh/` | public | `{refresh}` | 200 `{access, refresh}` (rotates) | blacklists old refresh |
| POST | `/auth/token/blacklist/` | staff/admin | `{refresh}` | 200/205 | logout |
| GET | `/auth/me/` | authenticated | — | 200 user profile | |
| GET | `/customers/` | staff | `?search=&page=&page_size=` | 200 paginated customers | search is case-insensitive name contains only |
| POST | `/customers/` | staff | `{name, initial_balance?, qr_id?}` | 201 customer; 400 validation | creates customer; if initial_balance>0 writes a `recharge` transaction with note `Initial balance loaded at registration.`; generates QR PNG |
| GET | `/customers/<public_id>/` | staff | — | 200 customer; 404 | |
| DELETE | `/customers/<public_id>/` | **admin** | — | 204; 404 | hard delete; cascades transactions; QR file remains |
| POST | `/customers/recharge/` | staff | `{customer_id: public_id, amount}` | 200 `{success:true, customer}`; 400/404 | amount min 0.01, max 99,999,999.99; row lock; adds `recharge` transaction |
| POST | `/customers/checkin/` | staff | `{customer_id?}` or `{qr_id?}` (one required) | 200 `{success:true, customer, fee_charged}`; 400/404/**402** | fee read from settings at call time; row lock; if balance<fee → 402 with `{"error":"Insufficient balance. Required: X, Available: Y"}`; writes `checkin` transaction with positive fee |
| GET | `/customers/<public_id>/qr/` | staff | — | 200 image/png; 404 | by public_id; file named by `qr_id`; regenerates if missing |
| GET | `/transactions/` | staff | `?type=recharge\|checkin&page=&page_size=` | 200 paginated transactions | invalid/absent type = no filter; newest first |
| GET | `/transactions/stats/` | staff | — | 200 `{total_customers,total_revenue,recent_transactions[10]}` | `total_revenue` = SUM of **recharge** amounts (regardless of UI label) |
| GET | `/settings/` | staff | — | 200 all seven settings as strings | defaults fill missing rows |
| POST | `/settings/` | **admin** | any subset of `{business_name,currency_symbol,checkin_fee,primary_color,primary_hover_color,logo_url,app_title}` | 200 all settings; 400 | partial update; unknown keys ignored; fee ≥0, ≤8 digits 2 decimals; colours must start `#` |
| GET | `/settings/branding/` | public | — | 200 `{business_name,primary_color,primary_hover_color,logo_url,app_title}` | used by login page |
| GET | `/analytics/` | staff | `?interval=hourly\|weekly\|monthly&metric=revenue\|checkins&date=YYYY-MM-DD` | 200 `{labels,data,display_date,interval,metric}` | see business rules |
| GET | `/media/qrcodes/<qr_id>.png` | (served by Django static in dev) | — | image/png | QR images |

No endpoint exists for editing a customer, listing per-customer transactions,
or user management. There is no `/api/health`.

### 2.1 Error statuses observed in code

- 400 `VALIDATION_FAILED`-like responses: invalid body, unknown customer
  identifiers, bad settings values, invalid UUID format.
- 401: missing/expired token.
- 403: authenticated non-staff, or staff on admin-only action.
- 404: customer not found; QR not found via detail.
- 402: insufficient balance on check-in (unusual but authoritative).
- 500: generic `{"error":"An internal server error occurred.","code":"server_error"}`.

---

## 3. Business rules

### 3.1 Check-in
1. Resolve customer by `qr_id` if provided, otherwise by `public_id`. Neither →
   400. Invalid UUID → service error (unhandled ValueError in some paths).
2. Fee = current `checkin_fee` setting (Decimal). Default `100.00`.
3. Lock the customer row (`select_for_update`).
4. If fee > 0 and balance < fee → `InsufficientBalanceError` → HTTP 402, no
   writes.
5. Subtract fee from balance, save.
6. Insert `Transaction(type=checkin, amount=+fee, notes='')`.
7. Response includes the updated customer and `fee_charged` as a **string**.
There is **no duplicate-scan debounce on the server**, no business-hours check,
and no inactive-customer concept. The frontend pauses ~2 s after a successful
scan and debounces decoding to ~100 ms/frame.

### 3.2 Recharge
1. Body requires `customer_id` (public UUID) and `amount` (Decimal ≥0.01,
   2 dp, max_digits 10).
2. Lock customer row.
3. Add amount to balance.
4. Insert `Transaction(type=recharge, amount=+amount, notes='')`.
No bonus rules, no upper bound beyond serializer max digits.

### 3.3 Customer creation
Name trimmed, non-empty, max 150. `initial_balance` ≥0, 2 dp. Optional
`qr_id` UUID lets an existing/pre-printed card be assigned. If the UUID is
already used, the DB unique constraint fails (500-ish). Initial positive
balance creates an audit `recharge` row. A QR PNG is generated at
`media/qrcodes/<qr_id>.png`.

### 3.4 Deletion
Hard delete is the only delete. Admin only. Cascade removes ledger rows.

### 3.5 Analytics definitions
- `revenue` = sum of `recharge.amount` in the bucket (**top-ups**, not check-in
  fees, despite the dashboard label "Checkin Revenue").
- `checkins` = count of `checkin` transactions in the bucket.
- `hourly`: 12 buckets, hours 9–20 inclusive, on the selected local date.
- `weekly`: Monday–Sunday week containing the selected date, labels like `Mon 24`.
- `monthly`: every calendar day of the selected month, labels `1..N`.
- `display_date`: `April 24, 2026` (hourly/weekly) or `April 2026` (monthly).
- Missing/invalid date → today; missing/invalid interval → hourly;
  invalid metric → revenue. All bucketing uses Django `TIME_ZONE`
  (`Asia/Kolkata`) because `USE_TZ=True` and `created_at__date` converts to
  local time.
- Data values are JSON **numbers/floats** (revenue) or ints (checkins).

### 3.6 Dashboard stats
- `total_customers` = all customer rows (hard-deleted customers disappear).
- `total_revenue` = SUM of recharge amounts, formatted by DRF as a string.
- `recent_transactions` = first 10 transactions in `-created_at` order.
- `next_offset` is mentioned in a docstring but **not returned**.

---

## 4. Auth

- Django REST Framework + SimpleJWT.
- `ACCESS_TOKEN_LIFETIME` default 8 hours; `REFRESH_TOKEN_LIFETIME` 7 days.
- HS256, `AUTH_HEADER_TYPES=('Bearer',)`, user id claim `user_id`.
- Refresh rotates and blacklists after rotation.
- Default permission is `IsAuthenticated`; views opt into Staff/Admin.
- No login rate limiting, no account lockout, no MFA.
- Passwords: Django PBKDF2-HMAC-SHA256 format
  `pbkdf2_sha256$<iterations>$<salt>$<base64 hash>`.

---

## 5. QR payload

- The encoded text is exactly `str(customer.qr_id)` where `qr_id` is a UUID:
  canonical 36-character lower-case hyphenated form, e.g.
  `ae5efc57-9b8d-4057-95a3-dddfd49d833f`.
- Generated with Python `qrcode`: version 1 (fit), error correction **H**,
  box size 10, border 4, black on white.
- Served as `/media/qrcodes/<qr_id>.png` in development.
- Scanner decodes the text and sends it as the JSON string `qr_id`.
- The Django UUID parser also accepts a 32-hex string (legacy Flask ids were
  32-hex). Old passes therefore still resolve, but newly generated images use
  the canonical dashed form. The Java migration will store the scanned text
  and resolve both forms.

---

## 6. Screens (React frontend)

| Screen | Route | Purpose | API calls | Important behaviour |
|---|---|---|---|---|
| Login | unauth state | Branded login | `GET /settings/branding/`, `POST /auth/token/` | username/password; inline error; show/hide password; login button disabled until both filled |
| Home | `/` | Full-screen tile menu | none (uses cached settings) | 6 tiles: Dashboard, Customers, Recharge, Check-in, Analytics, Settings; theme toggle; logout |
| Dashboard | `/dashboard` | KPIs + recent transactions | `GET /transactions/stats/`, `GET /transactions/?page=` | cards "Total Customers" and "Checkin Revenue" (actually recharge total); table columns Customer Name, Type, Amount, Date; infinite scroll; refresh |
| Customers | `/customers` | Manage customers/QR | `GET /customers/?search=`, `POST /customers/`, `DELETE /customers/<id>/`, QR image URL | table Name (+public id), Balance, Registered On, actions View QR / Delete; 300 ms debounced search; infinite scroll 10/page; "Issue New QR" (name, initial balance); "Assign Existing QR" (scan + name + balance); QR dialog with Download QR; deletion confirm says permanent |
| Recharge | `/recharge` | Top up wallet | `GET /customers/?search=`, `POST /customers/recharge/` | camera scanner auto-fills Customer ID; name autocomplete (300 ms); Customer ID read-only; amount field; quick amounts 100/500/1000; success toast with new balance |
| Check-in | `/scan` | Kiosk QR check-in | `POST /customers/checkin/` | camera select/start/stop; start by clicking placeholder; success overlay name + balance + `-{fee} Entry Fee`; error overlay with message; scan resumes after 2 s; shows standard entry fee |
| Analytics | `/analytics` | Charts | `GET /analytics/?interval=&metric=&date=` | metric toggle Customers/Revenue (UI sends `customers`, backend only understands `checkins`), bar/line, hourly/daily interval (UI sends `daily`, backend falls back to hourly), date picker and prev/next, persists choices in localStorage |
| Settings | `/settings` | Business + branding config | `GET /settings/`, `POST /settings/` | business name, check-in fee, currency symbol, app title, primary/hover colour pickers, save button |

Global shell: dark/light theme (default dark), collapsible sidebar with
Home/Dashboard/Customers/Recharge/Check-in/Analytics/Settings + Logout,
topbar title/user/theme/logout. Desktop window 1280×800, minimum 900×600.

User-visible strings of note: `Loading...`, `No customers found`,
`No recent transactions`, `Customer created successfully!`,
`Recharge successful! New balance: …`, `Checked in {name}`,
`Check-in Failed`, `Insufficient balance. Required: …, Available: …`,
`Camera Inactive`, `Start Scanner`, `Stop Scanner`, `No more customers`,
`Are you sure you want to permanently delete customer "…"?`.

---

## 7. Tauri / native capabilities

- Only one custom native command: `decode_qr_frame(frame_b64)` in
  `src-tauri/src/lib.rs`. It base64-decodes a PNG frame, converts to
  grayscale, and uses `rqrr` to find/decode the first QR grid. `"no_qr_found"`
  is a non-error sentinel.
- Webcam capture is done by the browser (`getUserMedia`), not Tauri.
- No file dialogs, printing, notifications, auto-update, tray, or protocol
  handlers are used.
- Bundle targets are NSIS/MSI; this is discarded by the migration (JAR only).
- Swing equivalents for Phase 5–6: direct webcam capture with
  `webcam-capture` + ZXing, `JFileChooser` for saving PNG/CSV,
  `PrinterJob` for printing QR/receipts, themed window instead of Tauri chrome.
  No auto-update.

---

## 8. Legacy / miscellaneous findings

- `backend/scripts/legacy_data_export.json` contains **26 named customers and
  27,500 transactions plus settings**, i.e. real-looking production data.
  It was not used for fixtures. See `QUESTIONS.md`.
- That export reveals the pre-Django Flask schema: `core_customer`
  (`id` 32-hex UUID string, `name`, `balance` float, `created_at` naive
  datetime), `core_transaction` (`id`, `customer_id`, `amount`, `type`,
  `created_at`), `core_setting` (`key`, `value`).
- `frontend_old/`, `legacy_assets/`, `downloads/Venuity_1.0.0_setup.exe`,
  `scratch_test_analytics.py`, `shell_test.py`, `.test-sprite.json` are
  legacy/scratch and are not ported (D8).
- Root `docker-compose.yml` targets PostgreSQL for Django; it is unrelated to
  the new MySQL stack and will remain untouched until cutover.
- `backend/media/qrcodes/ae5efc57-….png` is a single committed sample QR image.
- Python dependencies: Django ≥5, DRF, SimpleJWT, corsheaders, decouple,
  dj-database-url, psycopg2, pillow, qrcode, reportlab, pandas, openpyxl,
  gunicorn, pytest, black, flake8.

---

## 9. Phase 0 fixture and golden-file notes

- No `db.sqlite3` was committed, so a synthetic SQLite database matching the
  current Django schema is used instead of real data
  (`docs/migration/golden/sample.sqlite3`).
- Golden request/response pairs are captured with Django's test client against
  that synthetic database
  (`docs/migration/golden/tools/capture_golden.py`).
- The old backend can be run locally from a throwaway SQLite DB; nothing in
  production (`aaronschristo.pythonanywhere.com`) was called.
