import datetime
import random
import uuid

# Configuration
ZONE_OFFSET = datetime.timedelta(hours=5, minutes=30) # Asia/Kolkata is UTC+5:30
START_DATE = datetime.date(2026, 1, 1)
END_DATE = datetime.date(2026, 10, 30)

CUSTOMER_NAMES = [
    "Aarav Sharma", "Diya Patel", "Vivaan Gupta", "Ananya Reddy", "Kabir Mehta",
    "Ishaan Verma", "Aditi Rao", "Reyansh Joshi", "Saanvi Nair", "Arjun Kapoor",
    "Tara Sen", "Rishi Mukherjee", "Meera Pillai", "Devansh Bhatia", "Kiara Singh",
    "Rohan Kulkarni", "Tanvi Deshmukh", "Advait Joshi", "Anika Iyer", "Siddharth Menon",
    "Pooja Agarwal", "Karan Malhotra", "Riya Choudhury", "Nikhil Saxena", "Sanjana Bose",
    "Varun Nambiar", "Shalini Das", "Yashvardhan Singhania", "Bhavna Trivedi", "Sameer Chawla",
    "Kavya Hegde", "Manav Bansal", "Shruti Mishra", "Pranav Bhatt", "Sneha Ghosh",
    "Dhruv Ahuja", "Kritika Pandey", "Harshavardhan Rao", "Neha Venkatesh", "Alok Tripathi",
    "Isha Sethi", "Raghavendra Somani", "Natasha Kapoor", "Kunal Mittal", "Divya Raghavan",
    "Gaurav Kaushik", "Ritu Mahajan", "Chetan Nadkarni", "Lavanya Sundaram", "Madhavan Pillai"
]

TOPUP_AMOUNTS = [300.0, 500.0, 500.0, 1000.0, 1000.0, 1500.0, 2000.0]
TOPUP_NOTES = [
    "UPI payment recharge",
    "Credit card top-up",
    "Cash recharge at counter",
    "Weekend fun pack recharge",
    "Monthly play wallet topup",
    "GPay recharge"
]

CHECKIN_FEE = 100.0
USERS = [1, 2, 3] # admin, cashier1, sarang

def generate_sql():
    random.seed(42) # Deterministic for reproducibility

    sql_lines = []
    sql_lines.append("SET NAMES utf8mb4;")
    sql_lines.append("SET FOREIGN_KEY_CHECKS = 0;")
    sql_lines.append("START TRANSACTION;")

    # 1. Generate Customers
    customers = []
    customer_insert_vals = []
    base_id = 100 # Start at 100 to avoid conflicts with existing ids 1-25

    for i, name in enumerate(CUSTOMER_NAMES):
        cid = base_id + i
        pub_id = str(uuid.uuid4())
        pub_code = uuid.uuid4().hex
        phone = f"+91 98{random.randint(10, 99)} {random.randint(10000, 99999)}"
        # join date staggered in first 3 months
        join_day_offset = random.randint(0, 75)
        join_date = START_DATE + datetime.timedelta(days=join_day_offset)
        join_time = datetime.datetime.combine(join_date, datetime.time(hour=random.randint(10, 18), minute=random.randint(0, 59)))
        utc_join_time = join_time - ZONE_OFFSET
        ts_str = utc_join_time.strftime('%Y-%m-%d %H:%M:%S.000')

        customers.append({
            'id': cid,
            'name': name,
            'balance': 0.0,
            'active_from': join_date
        })

        escaped_name = name.replace("'", "''")
        customer_insert_vals.append(
            f"({cid}, '{pub_id}', '{pub_code}', '{escaped_name}', '{phone}', 0.00, 1, 0, '{ts_str}', '{ts_str}')"
        )

    sql_lines.append(
        "INSERT INTO customer (id, public_id, public_code, name, phone, wallet_balance, active, version, created_at, updated_at) VALUES\n"
        + ",\n".join(customer_insert_vals) + ";"
    )

    # 2. Generate Transactions & Check-ins day by day
    tx_insert_vals = []
    checkin_insert_vals = []
    tx_id = 1000 # Start at 1000 for new transactions

    curr_date = START_DATE
    while curr_date <= END_DATE:
        is_weekend = curr_date.weekday() >= 5
        is_friday = curr_date.weekday() == 4
        is_summer = 5 <= curr_date.month <= 6
        is_holiday = (curr_date.month == 1 and curr_date.day == 26) or \
                     (curr_date.month == 8 and curr_date.day == 15) or \
                     (curr_date.month == 10 and curr_date.day in (2, 20, 21, 22))

        # Determine daily visitor count
        if is_holiday:
            num_visits = random.randint(45, 75)
        elif is_weekend:
            num_visits = random.randint(35, 60)
        elif is_friday:
            num_visits = random.randint(18, 28)
        elif is_summer:
            num_visits = random.randint(16, 26)
        else:
            num_visits = random.randint(8, 18)

        # Available customers on this date
        available_custs = [c for c in customers if c['active_from'] <= curr_date]
        if not available_custs:
            available_custs = customers[:5]

        # Generate visits distributed throughout operating hours (10:00 to 20:59)
        day_events = []
        for _ in range(num_visits):
            c = random.choice(available_custs)
            
            # Hourly distribution weights:
            # 10-12: medium, 12-14: medium-high, 14-16: lower, 16-19: peak, 19-21: tapering
            hour_roll = random.random()
            if hour_roll < 0.25:
                hour = random.randint(10, 12)
            elif hour_roll < 0.40:
                hour = random.randint(12, 14)
            elif hour_roll < 0.50:
                hour = random.randint(14, 15)
            elif hour_roll < 0.85:
                hour = random.randint(16, 19)
            else:
                hour = 20
            
            minute = random.randint(0, 59)
            second = random.randint(0, 59)
            local_dt = datetime.datetime.combine(curr_date, datetime.time(hour, minute, second))
            day_events.append((local_dt, c))

        # Sort events chronologically for the day
        day_events.sort(key=lambda x: x[0])

        for local_dt, cust in day_events:
            user_id = random.choice(USERS)
            
            # If customer doesn't have enough balance, they do a TOPUP first (2 minutes earlier)
            if cust['balance'] < CHECKIN_FEE:
                topup_amt = random.choice(TOPUP_AMOUNTS)
                cust['balance'] += topup_amt
                topup_local_dt = local_dt - datetime.timedelta(minutes=2)
                topup_utc_dt = topup_local_dt - ZONE_OFFSET
                topup_ts = topup_utc_dt.strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]
                topup_note = random.choice(TOPUP_NOTES).replace("'", "''")

                tx_insert_vals.append(
                    f"({tx_id}, {cust['id']}, 'TOPUP', {topup_amt:.2f}, {cust['balance']:.2f}, '{topup_note}', {user_id}, '{topup_ts}')"
                )
                tx_id += 1

            # Now perform the CHECKIN
            cust['balance'] -= CHECKIN_FEE
            checkin_utc_dt = local_dt - ZONE_OFFSET
            checkin_ts = checkin_utc_dt.strftime('%Y-%m-%d %H:%M:%S.%f')[:-3]
            checkin_note = "Play area admission - 1hr session"

            tx_insert_vals.append(
                f"({tx_id}, {cust['id']}, 'CHECKIN', -{CHECKIN_FEE:.2f}, {cust['balance']:.2f}, '{checkin_note}', {user_id}, '{checkin_ts}')"
            )

            # Matching check_in table entry
            checkin_insert_vals.append(
                f"({cust['id']}, {tx_id}, {CHECKIN_FEE:.2f}, '{checkin_ts}')"
            )
            tx_id += 1

        curr_date += datetime.timedelta(days=1)

    # Chunk transaction inserts into batches of 1000 to keep SQL statements fast and within MySQL packet limits
    BATCH_SIZE = 1000
    for i in range(0, len(tx_insert_vals), BATCH_SIZE):
        batch = tx_insert_vals[i:i + BATCH_SIZE]
        sql_lines.append(
            "INSERT INTO wallet_transaction (id, customer_id, type, amount, balance_after, note, created_by, created_at) VALUES\n"
            + ",\n".join(batch) + ";"
        )

    for i in range(0, len(checkin_insert_vals), BATCH_SIZE):
        batch = checkin_insert_vals[i:i + BATCH_SIZE]
        sql_lines.append(
            "INSERT INTO check_in (customer_id, transaction_id, fee_charged, checked_in_at) VALUES\n"
            + ",\n".join(batch) + ";"
        )

    # 3. Update final customer wallet balances to match their ledger state
    for c in customers:
        sql_lines.append(
            f"UPDATE customer SET wallet_balance = {c['balance']:.2f} WHERE id = {c['id']};"
        )

    sql_lines.append("COMMIT;")
    sql_lines.append("SET FOREIGN_KEY_CHECKS = 1;")

    return "\n".join(sql_lines), len(customers), len(tx_insert_vals), len(checkin_insert_vals)

if __name__ == '__main__':
    sql_text, num_custs, num_tx, num_checkins = generate_sql()
    out_file = "seed_data.sql"
    with open(out_file, "w", encoding="utf-8") as f:
        f.write(sql_text)
    print(f"Generated {out_file}: {num_custs} customers, {num_tx} transactions, {num_checkins} check-ins.")

