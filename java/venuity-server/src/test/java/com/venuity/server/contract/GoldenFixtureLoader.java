package com.venuity.server.contract;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class GoldenFixtureLoader {

    private GoldenFixtureLoader() {
    }

    static void load(Path sqlite, String mysqlUrl, String mysqlUser, String mysqlPassword)
            throws SQLException {
        try (Connection source = DriverManager.getConnection("jdbc:sqlite:" + sqlite);
             Connection target = DriverManager.getConnection(jdbcUrl(mysqlUrl), mysqlUser, mysqlPassword)) {
            target.setAutoCommit(false);
            try {
                clearTarget(target);
                loadUsers(source, target);
                loadCustomers(source, target);
                List<CheckInRow> checkIns = loadTransactions(source, target);
                loadCheckIns(target, checkIns);
                loadSettings(source, target);
                target.commit();
            } catch (Exception exception) {
                target.rollback();
                throw exception;
            }
        }
    }

    static String jdbcUrl(String baseUrl) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator
                + "connectionTimeZone=UTC&useUnicode=true&characterEncoding=UTF-8"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
    }

    private static void clearTarget(Connection target) throws SQLException {
        try (Statement statement = target.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            statement.executeUpdate("DELETE FROM idempotency_record");
            statement.executeUpdate("DELETE FROM check_in");
            statement.executeUpdate("DELETE FROM wallet_transaction");
            statement.executeUpdate("DELETE FROM customer");
            statement.executeUpdate("DELETE FROM app_user");
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    private static void loadUsers(Connection source, Connection target) throws SQLException {
        String sql = """
                INSERT INTO app_user
                    (id, public_id, username, password_hash, role, enabled, email,
                     first_name, last_name, last_login, date_joined)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement select = source.prepareStatement(
                "SELECT * FROM users_user ORDER BY id");
             ResultSet rows = select.executeQuery();
             PreparedStatement insert = target.prepareStatement(sql)) {
            while (rows.next()) {
                boolean superuser = rows.getInt("is_superuser") == 1;
                boolean staff = rows.getInt("is_staff") == 1;
                boolean active = rows.getInt("is_active") == 1;
                insert.setLong(1, rows.getLong("id"));
                insert.setString(2, dashedUuid(rows.getString("public_id")));
                insert.setString(3, rows.getString("username"));
                insert.setString(4, rows.getString("password"));
                insert.setString(5, superuser ? "ADMIN" : "STAFF");
                insert.setBoolean(6, active && (superuser || staff));
                insert.setString(7, nullToEmpty(rows.getString("email")));
                insert.setString(8, nullToEmpty(rows.getString("first_name")));
                insert.setString(9, nullToEmpty(rows.getString("last_name")));
                insert.setTimestamp(10, timestamp(parseUtc(rows.getString("last_login"))));
                insert.setTimestamp(11, timestamp(parseUtc(rows.getString("date_joined"))));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void loadCustomers(Connection source, Connection target) throws SQLException {
        String sql = """
                INSERT INTO customer
                    (id, public_id, public_code, name, phone, wallet_balance, active,
                     version, created_at, updated_at)
                VALUES (?, ?, ?, ?, NULL, ?, TRUE, 0, ?, ?)
                """;
        try (PreparedStatement select = source.prepareStatement(
                "SELECT * FROM customers_customer ORDER BY id");
             ResultSet rows = select.executeQuery();
             PreparedStatement insert = target.prepareStatement(sql)) {
            while (rows.next()) {
                Instant createdAt = parseUtc(rows.getString("created_at"));
                insert.setLong(1, rows.getLong("id"));
                insert.setString(2, dashedUuid(rows.getString("public_id")));
                insert.setString(3, dashedUuid(rows.getString("qr_id")));
                insert.setString(4, rows.getString("name"));
                insert.setBigDecimal(5, money(rows.getBigDecimal("balance")));
                insert.setTimestamp(6, timestamp(createdAt));
                insert.setTimestamp(7, timestamp(createdAt));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static List<CheckInRow> loadTransactions(Connection source, Connection target)
            throws SQLException {
        Map<Long, List<SourceTransaction>> byCustomer = new LinkedHashMap<>();
        try (Statement select = source.createStatement();
             ResultSet rows = select.executeQuery(
                     "SELECT * FROM transactions_transaction ORDER BY customer_id, created_at, id")) {
            while (rows.next()) {
                SourceTransaction transaction = new SourceTransaction(
                        rows.getLong("id"),
                        rows.getLong("customer_id"),
                        rows.getString("transaction_type"),
                        money(rows.getBigDecimal("amount")),
                        nullToEmpty(rows.getString("notes")),
                        parseUtc(rows.getString("created_at")));
                byCustomer.computeIfAbsent(transaction.customerId(), ignored -> new ArrayList<>())
                        .add(transaction);
            }
        }

        String sql = """
                INSERT INTO wallet_transaction
                    (id, customer_id, type, amount, balance_after, note, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, NULL, ?)
                """;
        List<CheckInRow> checkIns = new ArrayList<>();
        try (PreparedStatement insert = target.prepareStatement(sql)) {
            for (List<SourceTransaction> customerTransactions : byCustomer.values()) {
                customerTransactions.sort(Comparator
                        .comparing(SourceTransaction::createdAt)
                        .thenComparingLong(SourceTransaction::id));
                BigDecimal running = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
                for (SourceTransaction transaction : customerTransactions) {
                    boolean checkIn = transaction.type().equals("checkin");
                    BigDecimal signed = checkIn ? transaction.amount().negate() : transaction.amount();
                    running = running.add(signed);
                    BigDecimal storedBalance = running.max(BigDecimal.ZERO.setScale(2));
                    insert.setLong(1, transaction.id());
                    insert.setLong(2, transaction.customerId());
                    insert.setString(3, checkIn ? "CHECKIN" : "TOPUP");
                    insert.setBigDecimal(4, signed);
                    insert.setBigDecimal(5, storedBalance);
                    insert.setString(6, transaction.note());
                    insert.setTimestamp(7, timestamp(transaction.createdAt()));
                    insert.addBatch();
                    if (checkIn) {
                        checkIns.add(new CheckInRow(transaction.customerId(), transaction.id(),
                                transaction.amount(), transaction.createdAt()));
                    }
                }
            }
            insert.executeBatch();
        }
        return checkIns;
    }

    private static void loadCheckIns(Connection target, List<CheckInRow> checkIns)
            throws SQLException {
        String sql = """
                INSERT INTO check_in (customer_id, transaction_id, fee_charged, checked_in_at)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement insert = target.prepareStatement(sql)) {
            for (CheckInRow checkIn : checkIns) {
                insert.setLong(1, checkIn.customerId());
                insert.setLong(2, checkIn.transactionId());
                insert.setBigDecimal(3, checkIn.fee());
                insert.setTimestamp(4, timestamp(checkIn.checkedInAt()));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void loadSettings(Connection source, Connection target) throws SQLException {
        Map<String, String> values = new LinkedHashMap<>();
        try (Statement select = source.createStatement();
             ResultSet rows = select.executeQuery(
                     "SELECT key, value FROM settings_app_applicationsetting")) {
            while (rows.next()) {
                values.put(rows.getString("key"), rows.getString("value"));
            }
        }
        String sql = """
                UPDATE business_settings
                   SET business_name = ?, currency_symbol = ?, checkin_fee = ?,
                       primary_color = ?, primary_hover_color = ?, logo_url = ?, app_title = ?
                 WHERE id = 1
                """;
        try (PreparedStatement update = target.prepareStatement(sql)) {
            update.setString(1, values.get("business_name"));
            update.setString(2, values.get("currency_symbol"));
            update.setBigDecimal(3, money(new BigDecimal(values.get("checkin_fee"))));
            update.setString(4, values.get("primary_color"));
            update.setString(5, values.get("primary_hover_color"));
            update.setString(6, values.get("logo_url"));
            update.setString(7, values.get("app_title"));
            update.executeUpdate();
        }
    }

    private static Instant parseUtc(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value.trim().replace(' ', 'T'),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC);
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String dashedUuid(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() != 32 || !normalized.matches("[0-9a-f]{32}")) {
            return normalized;
        }
        return normalized.substring(0, 8) + "-"
                + normalized.substring(8, 12) + "-"
                + normalized.substring(12, 16) + "-"
                + normalized.substring(16, 20) + "-"
                + normalized.substring(20);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record SourceTransaction(long id, long customerId, String type, BigDecimal amount,
                                     String note, Instant createdAt) {
    }

    private record CheckInRow(long customerId, long transactionId, BigDecimal fee,
                              Instant checkedInAt) {
    }
}
