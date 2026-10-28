"""Build a SYNTHETIC SQLite database for Phase 0/4 fixtures.

This script never reads the committed legacy export. It creates a throwaway
Django database at backend/db.sqlite3, seeds deterministic synthetic data, and
copies the result to docs/migration/golden/sample.sqlite3.

Usage (from repo root, with the migration venv):
    python docs/migration/golden/tools/setup_sample.py
"""

import os
import shutil
import sys
from datetime import datetime, timedelta
from decimal import Decimal
from pathlib import Path
from zoneinfo import ZoneInfo

REPO = Path(__file__).resolve().parents[4]
BACKEND = REPO / "backend"
sys.path.insert(0, str(BACKEND))
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")
os.environ.setdefault("SECRET_KEY", "synthetic-phase0-only")
os.environ.setdefault("DEBUG", "True")

import django  # noqa: E402

django.setup()

from django.core.management import call_command  # noqa: E402

DB_PATH = BACKEND / "db.sqlite3"
SAMPLE_PATH = REPO / "docs" / "migration" / "golden" / "sample.sqlite3"
TZ = ZoneInfo("Asia/Kolkata")


def local(y, mo, d, h=10, mi=0):
    return datetime(y, mo, d, h, mi, tzinfo=TZ)


# (public_id, qr_id, name, target_balance, extra transactions, active_analytics)
CUSTOMERS = [
    ("00000000-0000-4000-8000-000000000001", "10000000-0000-4000-8000-000000000001", "Asha Rao", Decimal("250.00")),
    ("00000000-0000-4000-8000-000000000002", "10000000-0000-4000-8000-000000000002", "José Núñez", Decimal("0.00")),
    ("00000000-0000-4000-8000-000000000003", "10000000-0000-4000-8000-000000000003", "李雷", Decimal("0.01")),
    ("00000000-0000-4000-8000-000000000004", "10000000-0000-4000-8000-000000000004", "Zoë O'Brien", Decimal("12.34")),
    ("00000000-0000-4000-8000-000000000005", "10000000-0000-4000-8000-000000000005", "Arjun Mehta", Decimal("99.99")),
    ("00000000-0000-4000-8000-000000000006", "10000000-0000-4000-8000-000000000006", "Meera Iyer", Decimal("1000.00")),
    ("00000000-0000-4000-8000-000000000007", "10000000-0000-4000-8000-000000000007", "Noor Al-Farsi", Decimal("9999.99")),
    ("00000000-0000-4000-8000-000000000008", "10000000-0000-4000-8000-000000000008", "Élodie Martin", Decimal("100000.00")),
    ("00000000-0000-4000-8000-000000000009", "10000000-0000-4000-8000-000000000009", "山田 太郎", Decimal("1234567.89")),
    ("00000000-0000-4000-8000-00000000000a", "10000000-0000-4000-8000-00000000000a", "Bob Smith", Decimal("42.00")),
    ("00000000-0000-4000-8000-00000000000b", "10000000-0000-4000-8000-00000000000b", "Alice Johnson", Decimal("777.77")),
    ("00000000-0000-4000-8000-00000000000c", "10000000-0000-4000-8000-00000000000c", "Test Zero", Decimal("0.00")),
]

# Analytics rows on 2026-04-20, labelled by local hour.
ANALYTICS = [
    (0, "recharge", Decimal("1500.00"), 9),
    (1, "recharge", Decimal("200.50"), 10),
    (2, "checkin", Decimal("100.00"), 10),
    (3, "checkin", Decimal("100.00"), 11),
    (4, "recharge", Decimal("75.25"), 12),
    (5, "checkin", Decimal("100.00"), 13),
    (6, "recharge", Decimal("300.00"), 14),
    (7, "checkin", Decimal("100.00"), 15),
    (8, "recharge", Decimal("45.00"), 18),
    (9, "checkin", Decimal("100.00"), 19),
    (10, "recharge", Decimal("10.00"), 20),
]


def run():
    from apps.customers.models import Customer
    from apps.settings_app.services import DEFAULTS
    from apps.settings_app.models import ApplicationSetting
    from apps.transactions.models import Transaction
    from django.contrib.auth import get_user_model

    if DB_PATH.exists():
        DB_PATH.unlink()
    call_command("migrate", verbosity=0, interactive=False)

    User = get_user_model()
    admin = User.objects.create_superuser("admin", "admin@example.invalid", "Admin#12345")
    admin.is_staff = True
    admin.save()
    User.objects.create_user("staff", "staff@example.invalid", "Staff#12345", is_staff=True)
    User.objects.create_user("inactive", "inactive@example.invalid", "Inactive#12345", is_staff=False)

    # Settings: source defaults, but a deterministic business name.
    for key, value in DEFAULTS.items():
        ApplicationSetting.objects.update_or_create(
            key=key,
            defaults={"value": "Synthetic PlayArea" if key == "business_name" else value},
        )

    customers = {}
    for n, (public_id, qr_id, name, target) in enumerate(CUSTOMERS):
        c = Customer.objects.create(
            public_id=public_id,
            qr_id=qr_id,
            name=name,
            balance=Decimal("0.00"),
            created_at=local(2026, 4, 1 + (n % 20), 10, n),
        )
        customers[n] = c

    # Analytics transactions.
    for n, tx_type, amount, hour in ANALYTICS:
        c = customers[n]
        Transaction.objects.create(
            customer=c,
            transaction_type=tx_type,
            amount=amount,
            notes="",
            created_at=local(2026, 4, 20, hour, 0),
        )

    # A couple of extra rows in other weeks/months for monthly/weekly fixtures.
    for n, tx_type, amount, day in [
        (0, "recharge", Decimal("60.00"), 6),
        (4, "checkin", Decimal("100.00"), 18),
        (5, "recharge", Decimal("125.00"), 4),
        (6, "recharge", Decimal("500.00"), 8),
    ]:
        Transaction.objects.create(
            customer=customers[n],
            transaction_type=tx_type,
            amount=amount,
            notes="Synthetic fixture",
            created_at=local(2026, 3, day, 12, 0),
        )

    # Make the cached balance exactly the target by inserting one balanced
    # opening row per customer.
    for n, (_, _, _, target) in enumerate(CUSTOMERS):
        c = customers[n]
        rows = Transaction.objects.filter(customer=c)
        net = sum(
            (r.amount if r.transaction_type == "recharge" else -r.amount for r in rows),
            Decimal("0.00"),
        )
        opening = target - net
        if opening > 0:
            Transaction.objects.create(
                customer=c,
                transaction_type="recharge",
                amount=opening,
                notes="Synthetic opening balance",
                created_at=local(2026, 1, 1, 9, n),
            )
        elif opening < 0:
            Transaction.objects.create(
                customer=c,
                transaction_type="checkin",
                amount=-opening,
                notes="Synthetic reconciliation",
                created_at=local(2026, 1, 1, 9, n),
            )
        c.balance = target
        c.save(update_fields=["balance"])

    SAMPLE_PATH.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(DB_PATH, SAMPLE_PATH)

    total_net = Decimal("0.00")
    mismatches = 0
    for c in Customer.objects.all():
        net = sum(
            (r.amount if r.transaction_type == "recharge" else -r.amount
             for r in Transaction.objects.filter(customer=c)),
            Decimal("0.00"),
        )
        total_net += c.balance
        if net != c.balance:
            mismatches += 1
    print(f"customers={Customer.objects.count()}")
    print(f"transactions={Transaction.objects.count()}")
    print(f"total_balance={total_net}")
    print(f"ledger_mismatches={mismatches}")
    print(f"sample={SAMPLE_PATH}")


if __name__ == "__main__":
    run()
