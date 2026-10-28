"""Capture golden HTTP request/response pairs from the Django source backend.

Runs against the synthetic DB built by setup_sample.py (backend/db.sqlite3).
The DB is mutated by write cases; it is disposable. Run setup_sample.py again
to reset it. No production endpoint and no committed real data are used.

Usage (from repo root):
    python docs/migration/golden/tools/capture_golden.py
"""

import hashlib
import json
import os
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
BACKEND = REPO / "backend"
GOLDEN = REPO / "docs" / "migration" / "golden"
sys.path.insert(0, str(BACKEND))
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")
os.environ.setdefault("SECRET_KEY", "synthetic-phase0-only")
os.environ.setdefault("DEBUG", "True")

import django  # noqa: E402

django.setup()

from django.conf import settings  # noqa: E402
from django.test import Client  # noqa: E402

if "testserver" not in settings.ALLOWED_HOSTS:
    settings.ALLOWED_HOSTS = list(settings.ALLOWED_HOSTS) + ["testserver"]

CASES = []


def redact(value):
    """Remove credentials from captured response bodies."""
    if isinstance(value, dict):
        return {
            k: ("<redacted>" if k in {"access", "refresh", "password"} else redact(v))
            for k, v in value.items()
        }
    if isinstance(value, list):
        return [redact(v) for v in value]
    return value


def call(client, name, method, path, *, auth=None, body=None, note=""):
    headers = {}
    if auth:
        headers["Authorization"] = f"Bearer {TOKENS[auth]}"
    fn = getattr(client, method.lower())
    kwargs = {"HTTP_AUTHORIZATION": headers.get("Authorization", "")}
    if body is not None:
        kwargs["data"] = json.dumps(body)
        kwargs["content_type"] = "application/json"
    resp = fn(path, **kwargs)
    record = {
        "name": name,
        "note": note,
        "request": {
            "method": method,
            "path": path,
            "auth": auth,
            "body": body,
        },
        "response": {
            "status": resp.status_code,
            "content_type": resp.headers.get("Content-Type"),
        },
    }
    if resp.headers.get("Content-Type", "").startswith("image/"):
        if getattr(resp, "streaming", False):
            raw = b"".join(resp.streaming_content)
        else:
            raw = resp.content
        digest = hashlib.sha256(raw).hexdigest()
        png = GOLDEN / f"{name}.png"
        png.write_bytes(raw)
        record["response"]["body"] = {
            "binary": png.name,
            "bytes": len(raw),
            "sha256": digest,
        }
    else:
        try:
            record["response"]["body"] = redact(resp.json())
        except Exception:
            record["response"]["body"] = resp.content.decode("utf-8", "replace")
    CASES.append(record)
    (GOLDEN / f"{name}.json").write_text(
        json.dumps(record, indent=2, ensure_ascii=False, default=str) + "\n",
        encoding="utf-8",
    )
    return resp


def login(client, username, password):
    resp = client.post(
        "/api/v1/auth/token/",
        data=json.dumps({"username": username, "password": password}),
        content_type="application/json",
    )
    assert resp.status_code == 200, resp.content
    return resp.json()


def main():
    GOLDEN.mkdir(parents=True, exist_ok=True)
    for old in GOLDEN.glob("*.json"):
        old.unlink()
    for old in GOLDEN.glob("*.png"):
        if old.name != "sample.sqlite3":
            old.unlink()
    CASES.clear()

    global TOKENS
    c = Client()
    admin = login(c, "admin", "Admin#12345")
    staff = login(c, "staff", "Staff#12345")
    TOKENS = {"admin": admin["access"], "staff": staff["access"]}

    # --- auth ---
    call(c, "auth-login-success", "POST", "/api/v1/auth/token/",
         body={"username": "admin", "password": "Admin#12345"})
    call(c, "auth-login-bad-password", "POST", "/api/v1/auth/token/",
         body={"username": "admin", "password": "wrong"})
    call(c, "auth-me-admin", "GET", "/api/v1/auth/me/", auth="admin")
    call(c, "auth-me-unauthenticated", "GET", "/api/v1/auth/me/")
    call(c, "auth-refresh", "POST", "/api/v1/auth/token/refresh/",
         body={"refresh": admin["refresh"]})

    # --- public settings/branding ---
    call(c, "settings-branding-public", "GET", "/api/v1/settings/branding/")
    call(c, "settings-get-unauthenticated", "GET", "/api/v1/settings/")

    # --- settings ---
    call(c, "settings-get-staff", "GET", "/api/v1/settings/", auth="staff")
    call(c, "settings-update-admin", "POST", "/api/v1/settings/", auth="admin",
         body={"business_name": "Synthetic PlayArea Updated", "checkin_fee": "120.00",
               "currency_symbol": "Rs", "primary_color": "#112233",
               "primary_hover_color": "#445566", "app_title": "Venuity Test"},
         note="Mutates settings; later cases expect fee 120.00")
    call(c, "settings-update-staff-forbidden", "POST", "/api/v1/settings/", auth="staff",
         body={"checkin_fee": "1.00"})
    call(c, "settings-update-invalid-fee", "POST", "/api/v1/settings/", auth="admin",
         body={"checkin_fee": "-5.00"})
    call(c, "settings-update-invalid-colour", "POST", "/api/v1/settings/", auth="admin",
         body={"primary_color": "red"})

    # --- customers read ---
    call(c, "customers-list-page1", "GET", "/api/v1/customers/?page=1&page_size=5", auth="staff")
    call(c, "customers-search", "GET", "/api/v1/customers/?search=asha", auth="staff")
    call(c, "customers-search-none", "GET", "/api/v1/customers/?search=zzzz-nobody", auth="staff")
    call(c, "customers-detail", "GET",
         "/api/v1/customers/00000000-0000-4000-8000-000000000001/", auth="staff")
    call(c, "customers-detail-not-found", "GET",
         "/api/v1/customers/00000000-0000-4000-8000-00000000ffff/", auth="staff")
    call(c, "customers-qr-png", "GET",
         "/api/v1/customers/00000000-0000-4000-8000-000000000001/qr/", auth="staff")
    call(c, "customers-list-unauthenticated", "GET", "/api/v1/customers/")

    # --- transactions / stats ---
    call(c, "transactions-list-page1", "GET", "/api/v1/transactions/?page=1&page_size=5", auth="staff")
    call(c, "transactions-filter-recharge", "GET", "/api/v1/transactions/?type=recharge", auth="staff")
    call(c, "transactions-filter-checkin", "GET", "/api/v1/transactions/?type=checkin", auth="staff")
    call(c, "transactions-stats", "GET", "/api/v1/transactions/stats/", auth="staff")

    # --- analytics (before further mutations) ---
    call(c, "analytics-hourly-revenue", "GET",
         "/api/v1/analytics/?interval=hourly&metric=revenue&date=2026-04-20", auth="staff")
    call(c, "analytics-hourly-checkins", "GET",
         "/api/v1/analytics/?interval=hourly&metric=checkins&date=2026-04-20", auth="staff")
    call(c, "analytics-weekly-revenue", "GET",
         "/api/v1/analytics/?interval=weekly&metric=revenue&date=2026-04-20", auth="staff")
    call(c, "analytics-monthly-checkins", "GET",
         "/api/v1/analytics/?interval=monthly&metric=checkins&date=2026-04-20", auth="staff")
    call(c, "analytics-invalid-date", "GET",
         "/api/v1/analytics/?interval=hourly&metric=revenue&date=not-a-date", auth="staff")

    # --- recharge ---
    call(c, "recharge-success", "POST", "/api/v1/customers/recharge/", auth="staff",
         body={"customer_id": "00000000-0000-4000-8000-000000000001", "amount": "50.25"},
         note="Mutates Asha's balance")
    call(c, "recharge-amount-too-small", "POST", "/api/v1/customers/recharge/", auth="staff",
         body={"customer_id": "00000000-0000-4000-8000-000000000001", "amount": "0.00"})
    call(c, "recharge-zero-balance-customer", "POST", "/api/v1/customers/recharge/", auth="staff",
         body={"customer_id": "00000000-0000-4000-8000-000000000002", "amount": "10.00"},
         note="Mutates José's balance")
    call(c, "recharge-customer-not-found", "POST", "/api/v1/customers/recharge/", auth="staff",
         body={"customer_id": "00000000-0000-4000-8000-00000000ffff", "amount": "10.00"})

    # --- check-in ---
    call(c, "checkin-success-qr", "POST", "/api/v1/customers/checkin/", auth="staff",
         body={"qr_id": "10000000-0000-4000-8000-000000000006"},
         note="Mutates Meera's balance; fee is 120.00 after settings update")
    call(c, "checkin-insufficient", "POST", "/api/v1/customers/checkin/", auth="staff",
         body={"qr_id": "10000000-0000-4000-8000-000000000003"},
         note="Li Lei has 0.01")
    call(c, "checkin-invalid-qr", "POST", "/api/v1/customers/checkin/", auth="staff",
         body={"qr_id": "10000000-0000-4000-8000-00000000ffff"})
    call(c, "checkin-missing-identifier", "POST", "/api/v1/customers/checkin/", auth="staff",
         body={})
    call(c, "checkin-by-public-id", "POST", "/api/v1/customers/checkin/", auth="staff",
         body={"customer_id": "00000000-0000-4000-8000-00000000000b"},
         note="Mutates Alice's balance")

    # --- create / delete ---
    created = call(c, "customers-create", "POST", "/api/v1/customers/", auth="staff",
                   body={"name": "Synthetic New Customer", "initial_balance": "15.50",
                         "qr_id": "30000000-0000-4000-8000-000000000001"})
    created_body = created.json()
    public_id = created_body.get("public_id")
    call(c, "customers-create-empty-name", "POST", "/api/v1/customers/", auth="staff",
         body={"name": "   "})
    call(c, "customers-delete-staff-forbidden", "DELETE", f"/api/v1/customers/{public_id}/",
         auth="staff")
    call(c, "customers-delete-admin", "DELETE", f"/api/v1/customers/{public_id}/", auth="admin")

    # --- routing / auth ---
    call(c, "unknown-route", "GET", "/api/v1/does-not-exist/", auth="admin")

    print(f"captured {len(CASES)} cases in {GOLDEN}")


if __name__ == "__main__":
    main()
