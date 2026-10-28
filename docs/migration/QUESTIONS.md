# Questions for the human

> Tasks that depend on an answer are paused; independent work continues.

## Q-01 (URGENT) — Real customer data committed to the repository

`backend/scripts/legacy_data_export.json` (5.3 MB) contains 26 named customers,
their balances, 27,500 transactions and the business settings. It appears to be
real production data. Per Section 17.2 of the migration plan I have **not**
copied or reused it anywhere.

Please decide:

1. May this file stay in the repository, or should it be removed (and purged
   from history)?
2. If it is safe synthetic data after all, may the migration tool use it for a
   reconciliation test? Otherwise, please provide a database dump out of band
   (do not commit it) or confirm that the synthetic fixture is sufficient.

No blocking action was taken; Phase 0 uses synthetic data.

## Q-02 — Legacy QR passes: dashed vs 32-hex payload

`docs/BACKEND.md` says pre-Django Flask ids were copied into `qr_id`; the export
shows 32-character hex ids. The current Django generator writes canonical
dashed UUID text. If any customer still carries a QR printed by the Flask build,
those stickers encode 32 hex characters.

The Java server will accept both forms when resolving a scan, so this is not
blocking. Please confirm that accepting both is acceptable, or tell us the exact
payload used for currently printed passes so we can assert it in tests.

## Q-03 — Production time zone

The source uses `Asia/Kolkata` for analytics bucketing. The new system defaults
`business_timezone=Asia/Kolkata`. Confirm if any venue operates in another zone.

## Q-04 — Legacy user passwords

Django PBKDF2 hashes can be migrated and re-hashed to BCrypt on first login.
Please confirm that is preferred over forcing every operator to reset their
password. (Default: implement the legacy encoder.)
