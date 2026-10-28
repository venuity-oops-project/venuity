package com.venuity.migrate;

import com.venuity.common.MoneyUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MigrationRunner {

    private static final int BATCH_SIZE = 500;
    private static final BigDecimal TARGET_MAX = new BigDecimal("9999999999.99");
    private static final Map<String, String> SETTINGS_DEFAULTS = Map.of(
            "business_name", "PlayArea Manager",
            "currency_symbol", "\u20b9",
            "checkin_fee", "100.00",
            "primary_color", "#6366f1",
            "primary_hover_color", "#4f46e5",
            "logo_url", "/logo.png",
            "app_title", "Venuity"
    );

    private final MigrationOptions options;

    public MigrationRunner(MigrationOptions options) {
        this.options = options;
    }

    public MigrationReport run() {
        MigrationReport report = new MigrationReport(
                options.dryRun(),
                options.sqlitePath().toAbsolutePath().toString(),
                redact(options.mysqlUrl())
        );
        SourceDatabase.SourceData source = SourceDatabase.read(
                options.sqlitePath(),
                options.sourceTimezone(),
                report
        );
        validateSource(source, report);

        try (Connection target = openTarget()) {
            boolean replaceExisting = preflight(target, source, report);
            target.setAutoCommit(false);
            try {
                if (replaceExisting) {
                    clearTarget(target);
                }
                writeUsers(target, source.users());
                writeSettings(target, source.settings(), report);
                writeCustomers(target, source.customers());
                Ledger ledger = buildLedger(source, report);
                writeTransactions(target, ledger.rows());
                writeCheckIns(target, ledger.checkIns());
                reconcile(target, source, ledger, report);
                if (options.dryRun()) {
                    target.rollback();
                } else {
                    target.commit();
                }
                report.finish(true);
                return report;
            } catch (MigrationException exception) {
                target.rollback();
                throw exception;
            } catch (SQLException exception) {
                target.rollback();
                throw new MigrationException("Migration write failed: " + exception.getMessage(),
                        exception);
            }
        } catch (MigrationException exception) {
            finishFailureReport(report, exception);
            throw exception;
        } catch (SQLException exception) {
            MigrationException failure = new MigrationException(
                    "MySQL connection failed: " + exception.getMessage(),
                    exception
            );
            finishFailureReport(report, failure);
            throw failure;
        }
    }

    private void finishFailureReport(MigrationReport report, RuntimeException failure) {
        report.finish(false);
        if (options.reportPath() == null) {
            return;
        }
        try {
            report.write(options.reportPath());
        } catch (RuntimeException reportFailure) {
            failure.addSuppressed(reportFailure);
        }
    }

    private Connection openTarget() throws SQLException {
        String separator = options.mysqlUrl().contains("?") ? "&" : "?";
        String url = options.mysqlUrl() + separator
                + "connectionTimeZone=UTC&useUnicode=true&characterEncoding=UTF-8"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
        return DriverManager.getConnection(url, options.mysqlUser(), options.mysqlPassword());
    }

    private void validateSource(SourceDatabase.SourceData source, MigrationReport report) {
        Set<Long> userIds = new HashSet<>();
        Set<String> userPublicIds = new HashSet<>();
        Set<String> usernames = new HashSet<>();
        for (SourceDatabase.User user : source.users()) {
            requireUnique(userIds, user.id(), "Duplicate users_user.id: " + user.id());
            String publicId = canonicalUuid(user.publicId(), "users_user.public_id for " + user.username());
            if (!userPublicIds.add(publicId)) {
                throw new MigrationException("Duplicate user public_id: " + publicId);
            }
            String username = requireText(user.username(), "users_user.username");
            if (!usernames.add(username)) {
                throw new MigrationException("Duplicate username: " + username);
            }
            String password = requireText(user.passwordHash(), "users_user.password for " + username);
            if (password.length() > 255) {
                throw new MigrationException("Password hash is too long for user " + username);
            }
            if (!password.startsWith("pbkdf2_sha256$")) {
                report.warning("User " + username + " does not use a Django PBKDF2-SHA256 hash.");
            }
            if (user.dateJoined() == null) {
                throw new MigrationException("users_user.date_joined is null for " + username);
            }
        }

        Set<Long> customerIds = new HashSet<>();
        Set<String> customerPublicIds = new HashSet<>();
        Set<String> customerCodes = new HashSet<>();
        BigDecimal sourceTotal = MoneyUtil.ZERO;
        for (SourceDatabase.Customer customer : source.customers()) {
            requireUnique(customerIds, customer.id(), "Duplicate customers_customer.id: " + customer.id());
            String publicId = canonicalUuid(
                    customer.publicId(),
                    "customers_customer.public_id for " + customer.name()
            );
            if (!customerPublicIds.add(publicId)) {
                throw new MigrationException("Duplicate customer public_id: " + publicId);
            }
            String publicCode = canonicalCode(
                    customer.qrId(),
                    "customers_customer.qr_id for " + customer.name()
            );
            if (!customerCodes.add(publicCode)) {
                throw new MigrationException("Duplicate customer QR identifier: " + publicCode);
            }
            requireText(customer.name(), "customers_customer.name");
            if (customer.createdAt() == null) {
                throw new MigrationException("customers_customer.created_at is null for "
                        + customer.name());
            }
            requireTargetMoney(customer.balance(), TARGET_MAX,
                    "customers_customer.balance for " + customer.name());
            sourceTotal = sourceTotal.add(customer.balance());
        }
        report.sourceTotalBalance(sourceTotal.setScale(2));

        Set<Long> transactionIds = new HashSet<>();
        for (SourceDatabase.Transaction transaction : source.transactions()) {
            requireUnique(transactionIds, transaction.id(),
                    "Duplicate transactions_transaction.id: " + transaction.id());
            if (!customerIds.contains(transaction.customerId())) {
                throw new MigrationException("Transaction " + transaction.id()
                        + " references missing customer " + transaction.customerId());
            }
            targetType(transaction.type());
            if (transaction.amount().signum() < 0) {
                throw new MigrationException("Transaction " + transaction.id()
                        + " has a negative amount.");
            }
            requireTargetMoney(transaction.amount(), MoneyUtil.MAX_AMOUNT,
                    "transactions_transaction.amount for id " + transaction.id());
            if (transaction.createdAt() == null) {
                throw new MigrationException("transactions_transaction.created_at is null for id "
                        + transaction.id());
            }
        }

        report.sourceCount(MigrationReport.USERS, source.users().size());
        report.sourceCount(MigrationReport.SETTINGS, source.settings().size());
        report.sourceCount(MigrationReport.CUSTOMERS, source.customers().size());
        report.sourceCount(MigrationReport.LEDGER_ROWS, source.transactions().size());
        report.sourceCount(
                MigrationReport.CHECK_INS,
                source.transactions().stream()
                        .filter(transaction -> "checkin".equalsIgnoreCase(transaction.type()))
                        .count()
        );
    }

    private boolean preflight(
            Connection target,
            SourceDatabase.SourceData source,
            MigrationReport report
    ) throws SQLException {
        Map<String, Long> before = countTarget(target);
        before.forEach(report::targetBeforeCount);
        long idempotencyRows = count(target, "idempotency_record");
        boolean hasData = before.get(MigrationReport.USERS) > 0
                || before.get(MigrationReport.CUSTOMERS) > 0
                || before.get(MigrationReport.LEDGER_ROWS) > 0
                || before.get(MigrationReport.CHECK_INS) > 0
                || idempotencyRows > 0;
        if (hasData && !options.forceEmptyCheckOff()) {
            throw new MigrationException(
                    "Target contains data. Refusing to migrate. "
                            + "Use --force-empty-check-off to delete existing data and replace it."
            );
        }
        long settingsRows = count(target, "business_settings");
        if (settingsRows != 1) {
            throw new MigrationException(
                    "Target business_settings must contain exactly one seeded row; found "
                            + settingsRows + ". Apply migrations V1 and V2 first."
            );
        }
        if (source.users().isEmpty()) {
            throw new MigrationException("SQLite source contains no users.");
        }
        if (source.customers().isEmpty()) {
            throw new MigrationException("SQLite source contains no customers.");
        }
        if (hasData) {
            report.warning("Forced replacement: existing target data will be deleted before import.");
            return true;
        }
        return false;
    }

    private void clearTarget(Connection target) throws SQLException {
        for (String table : List.of(
                "idempotency_record",
                "check_in",
                "wallet_transaction",
                "customer",
                "app_user"
        )) {
            try (Statement statement = target.createStatement()) {
                statement.executeUpdate("DELETE FROM " + table);
            }
        }
    }

    private Map<String, Long> countTarget(Connection target) throws SQLException {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(MigrationReport.USERS, count(target, "app_user"));
        counts.put(MigrationReport.SETTINGS, count(target, "business_settings"));
        counts.put(MigrationReport.CUSTOMERS, count(target, "customer"));
        counts.put(MigrationReport.LEDGER_ROWS, count(target, "wallet_transaction"));
        counts.put(MigrationReport.CHECK_INS, count(target, "check_in"));
        return counts;
    }

    private long count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        } catch (SQLException exception) {
            throw new MigrationException("Target table " + table
                    + " is missing or unreadable. Apply migrations V1-V3 first.", exception);
        }
    }

    private void writeUsers(Connection target, List<SourceDatabase.User> users) throws SQLException {
        String sql = """
                INSERT INTO app_user (
                    id, public_id, username, password_hash, role, enabled, email,
                    first_name, last_name, last_login, date_joined
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = target.prepareStatement(sql)) {
            int pending = 0;
            for (SourceDatabase.User user : users) {
                statement.setLong(1, user.id());
                statement.setString(2, canonicalUuid(
                        user.publicId(),
                        "users_user.public_id for " + user.username()
                ));
                statement.setString(3, user.username());
                statement.setString(4, user.passwordHash());
                statement.setString(5, user.superuser() ? "ADMIN" : "STAFF");
                statement.setBoolean(6, user.active() && (user.superuser() || user.staff()));
                statement.setString(7, nullToEmpty(user.email()));
                statement.setString(8, nullToEmpty(user.firstName()));
                statement.setString(9, nullToEmpty(user.lastName()));
                setTimestamp(statement, 10, user.lastLogin());
                setTimestamp(statement, 11, user.dateJoined());
                statement.addBatch();
                if (++pending == BATCH_SIZE) {
                    statement.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                statement.executeBatch();
            }
        }
    }

    private void writeSettings(
            Connection target,
            Map<String, String> source,
            MigrationReport report
    ) throws SQLException {
        Map<String, String> existing = existingSettings(target);
        String businessName = setting(source, existing, "business_name");
        String currencySymbol = setting(source, existing, "currency_symbol");
        BigDecimal checkinFee = SourceDatabase.money(
                setting(source, existing, "checkin_fee"),
                "settings_app_applicationsetting.checkin_fee",
                report
        );
        if (checkinFee.signum() < 0 || checkinFee.compareTo(MoneyUtil.MAX_AMOUNT) > 0) {
            throw new MigrationException("Settings check-in fee is out of range: " + checkinFee);
        }
        String sql = """
                UPDATE business_settings
                SET business_name = ?, currency_symbol = ?, checkin_fee = ?,
                    primary_color = ?, primary_hover_color = ?, logo_url = ?, app_title = ?
                WHERE id = 1
                """;
        try (PreparedStatement statement = target.prepareStatement(sql)) {
            statement.setString(1, businessName);
            statement.setString(2, currencySymbol);
            statement.setBigDecimal(3, checkinFee);
            statement.setString(4, setting(source, existing, "primary_color"));
            statement.setString(5, setting(source, existing, "primary_hover_color"));
            statement.setString(6, setting(source, existing, "logo_url"));
            statement.setString(7, setting(source, existing, "app_title"));
            if (statement.executeUpdate() != 1) {
                throw new MigrationException("Could not update the seeded business_settings row.");
            }
        }
        report.writtenCount(MigrationReport.SETTINGS, 1);
    }

    private Map<String, String> existingSettings(Connection target) throws SQLException {
        String sql = """
                SELECT business_name, currency_symbol, checkin_fee, primary_color,
                       primary_hover_color, logo_url, app_title
                FROM business_settings
                WHERE id = 1
                """;
        try (Statement statement = target.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) {
                throw new MigrationException("Target business_settings row id=1 is missing.");
            }
            Map<String, String> settings = new LinkedHashMap<>();
            settings.put("business_name", result.getString("business_name"));
            settings.put("currency_symbol", result.getString("currency_symbol"));
            settings.put("checkin_fee", result.getString("checkin_fee"));
            settings.put("primary_color", result.getString("primary_color"));
            settings.put("primary_hover_color", result.getString("primary_hover_color"));
            settings.put("logo_url", result.getString("logo_url"));
            settings.put("app_title", result.getString("app_title"));
            return settings;
        }
    }

    private String setting(Map<String, String> source, Map<String, String> existing, String key) {
        String value = source.get(key);
        if (value != null) {
            return value;
        }
        value = existing.get(key);
        if (value != null) {
            return value;
        }
        return SETTINGS_DEFAULTS.get(key);
    }

    private void writeCustomers(Connection target, List<SourceDatabase.Customer> customers)
            throws SQLException {
        String sql = """
                INSERT INTO customer (
                    id, public_id, public_code, name, phone, wallet_balance,
                    active, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = target.prepareStatement(sql)) {
            int pending = 0;
            for (SourceDatabase.Customer customer : customers) {
                statement.setLong(1, customer.id());
                statement.setString(2, canonicalUuid(
                        customer.publicId(),
                        "customers_customer.public_id for " + customer.name()
                ));
                statement.setString(3, canonicalCode(
                        customer.qrId(),
                        "customers_customer.qr_id for " + customer.name()
                ));
                statement.setString(4, customer.name());
                statement.setNull(5, Types.VARCHAR);
                statement.setBigDecimal(6, customer.balance());
                statement.setBoolean(7, true);
                statement.setLong(8, 0L);
                setTimestamp(statement, 9, customer.createdAt());
                setTimestamp(statement, 10, customer.createdAt());
                statement.addBatch();
                if (++pending == BATCH_SIZE) {
                    statement.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                statement.executeBatch();
            }
        }
    }

    private Ledger buildLedger(SourceDatabase.SourceData source, MigrationReport report) {
        Map<Long, List<SourceDatabase.Transaction>> byCustomer = new HashMap<>();
        for (SourceDatabase.Transaction transaction : source.transactions()) {
            byCustomer.computeIfAbsent(transaction.customerId(), key -> new ArrayList<>())
                    .add(transaction);
        }
        List<LedgerRow> rows = new ArrayList<>();
        List<CheckInRow> checkIns = new ArrayList<>();
        for (SourceDatabase.Customer customer : source.customers()) {
            List<SourceDatabase.Transaction> transactions = new ArrayList<>(
                    byCustomer.getOrDefault(customer.id(), List.of())
            );
            transactions.sort(Comparator.comparing(SourceDatabase.Transaction::createdAt)
                    .thenComparingLong(SourceDatabase.Transaction::id));
            BigDecimal running = MoneyUtil.ZERO;
            int clampedBalances = 0;
            for (SourceDatabase.Transaction transaction : transactions) {
                String type = targetType(transaction.type());
                BigDecimal signed = "TOPUP".equals(type)
                        ? transaction.amount()
                        : transaction.amount().negate();
                running = running.add(signed).setScale(2);
                if (running.abs().compareTo(TARGET_MAX) > 0) {
                    throw new MigrationException(
                            "Running balance is out of range for customer " + customer.id()
                                    + " (" + customer.name() + ") at transaction "
                                    + transaction.id()
                    );
                }
                BigDecimal storedBalance = running.max(MoneyUtil.ZERO);
                if (storedBalance.signum() == 0 && running.signum() < 0) {
                    clampedBalances++;
                }
                requireTargetMoney(storedBalance, TARGET_MAX,
                        "wallet_transaction.balance_after for customer " + customer.id());
                String note = transaction.note();
                if (note != null && note.length() > 1000) {
                    note = note.substring(0, 1000);
                    report.warning("Transaction " + transaction.id()
                            + " note truncated to 1000 characters.");
                }
                rows.add(new LedgerRow(
                        transaction.id(),
                        customer.id(),
                        type,
                        signed,
                        storedBalance,
                        note,
                        transaction.createdAt()
                ));
                if ("CHECKIN".equals(type)) {
                    checkIns.add(new CheckInRow(
                            customer.id(),
                            transaction.id(),
                            transaction.amount(),
                            transaction.createdAt()
                    ));
                }
            }
            if (clampedBalances > 0) {
                report.warning("Customer " + customer.id() + " (" + customer.name() + "): "
                        + clampedBalances + " historical balance_after value(s) clamped to 0.00 "
                        + "to satisfy the target nonnegative constraint.");
            }
            if (transactions.isEmpty()) {
                if (customer.balance().signum() > 0) {
                    rows.add(new LedgerRow(
                            null,
                            customer.id(),
                            "OPENING_BALANCE",
                            customer.balance(),
                            customer.balance(),
                            "Migrated opening balance",
                            customer.createdAt()
                    ));
                    report.openingBalance();
                }
                continue;
            }
            BigDecimal difference = customer.balance().subtract(running).setScale(2);
            if (difference.abs().compareTo(TARGET_MAX) > 0) {
                throw new MigrationException("Reconciliation amount is out of range for customer "
                        + customer.id() + ": " + difference.toPlainString());
            }
            if (difference.signum() != 0) {
                Instant adjustedAt = transactions.getLast().createdAt().plusMillis(1);
                BigDecimal adjusted = running.add(difference).setScale(2);
                requireTargetMoney(adjusted, TARGET_MAX,
                        "adjusted wallet balance for customer " + customer.id());
                rows.add(new LedgerRow(
                        null,
                        customer.id(),
                        "ADJUSTMENT",
                        difference,
                        adjusted,
                        "Migration reconciliation",
                        adjustedAt
                ));
                report.adjustment("customer " + customer.id() + " (" + customer.name()
                        + "): " + difference.toPlainString());
            }
        }
        return new Ledger(rows, checkIns);
    }

    private void writeTransactions(Connection target, List<LedgerRow> rows) throws SQLException {
        List<LedgerRow> imported = rows.stream()
                .filter(row -> row.sourceTransactionId() != null)
                .toList();
        List<LedgerRow> generated = rows.stream()
                .filter(row -> row.sourceTransactionId() == null)
                .toList();
        writeTransactionBatch(target, imported, true);
        writeTransactionBatch(target, generated, false);
    }

    private void writeTransactionBatch(
            Connection target,
            List<LedgerRow> rows,
            boolean explicitId
    ) throws SQLException {
        if (rows.isEmpty()) {
            return;
        }
        String sql = explicitId
                ? """
                  INSERT INTO wallet_transaction (
                      id, customer_id, type, amount, balance_after, note, created_by, created_at
                  ) VALUES (?, ?, ?, ?, ?, ?, NULL, ?)
                  """
                : """
                  INSERT INTO wallet_transaction (
                      customer_id, type, amount, balance_after, note, created_by, created_at
                  ) VALUES (?, ?, ?, ?, ?, NULL, ?)
                  """;
        try (PreparedStatement statement = target.prepareStatement(sql)) {
            int pending = 0;
            for (LedgerRow row : rows) {
                int index = 1;
                if (explicitId) {
                    statement.setLong(index++, row.sourceTransactionId());
                }
                statement.setLong(index++, row.customerId());
                statement.setString(index++, row.type());
                statement.setBigDecimal(index++, row.amount());
                statement.setBigDecimal(index++, row.balanceAfter());
                statement.setString(index, row.note());
                statement.setTimestamp(index + 1, timestamp(row.createdAt()));
                statement.addBatch();
                if (++pending == BATCH_SIZE) {
                    statement.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                statement.executeBatch();
            }
        }
    }

    private void writeCheckIns(Connection target, List<CheckInRow> checkIns) throws SQLException {
        if (checkIns.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO check_in (
                    customer_id, transaction_id, fee_charged, checked_in_at
                ) VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = target.prepareStatement(sql)) {
            int pending = 0;
            for (CheckInRow checkIn : checkIns) {
                statement.setLong(1, checkIn.customerId());
                statement.setLong(2, checkIn.transactionId());
                statement.setBigDecimal(3, checkIn.fee());
                setTimestamp(statement, 4, checkIn.checkedInAt());
                statement.addBatch();
                if (++pending == BATCH_SIZE) {
                    statement.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                statement.executeBatch();
            }
        }
    }

    private void reconcile(
            Connection target,
            SourceDatabase.SourceData source,
            Ledger ledger,
            MigrationReport report
    ) throws SQLException {
        Map<String, Long> after = countTarget(target);
        after.forEach(report::targetAfterCount);
        report.writtenCount(MigrationReport.USERS, source.users().size());
        report.writtenCount(MigrationReport.CUSTOMERS, source.customers().size());
        report.writtenCount(MigrationReport.LEDGER_ROWS, ledger.rows().size());
        report.writtenCount(MigrationReport.CHECK_INS, ledger.checkIns().size());

        long sourceCheckIns = source.transactions().stream()
                .filter(transaction -> "checkin".equalsIgnoreCase(transaction.type()))
                .count();
        if (after.get(MigrationReport.USERS) != source.users().size()
                || after.get(MigrationReport.CUSTOMERS) != source.customers().size()
                || after.get(MigrationReport.LEDGER_ROWS) != ledger.rows().size()
                || after.get(MigrationReport.CHECK_INS) != sourceCheckIns) {
            throw new MigrationException("Target row counts do not match the migration plan.");
        }

        Map<Long, BigDecimal> expectedBalances = new HashMap<>();
        for (SourceDatabase.Customer customer : source.customers()) {
            expectedBalances.put(customer.id(), customer.balance());
        }
        List<String> balanceMismatches = new ArrayList<>();
        try (Statement statement = target.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT id, wallet_balance FROM customer ORDER BY id")) {
            while (result.next()) {
                long id = result.getLong("id");
                BigDecimal expected = expectedBalances.remove(id);
                BigDecimal actual = result.getBigDecimal("wallet_balance").setScale(2);
                if (expected == null || expected.compareTo(actual) != 0) {
                    balanceMismatches.add("customer " + id + " expected " + expected
                            + " but found " + actual.toPlainString());
                }
            }
        }
        for (Long missing : expectedBalances.keySet()) {
            balanceMismatches.add("customer " + missing + " is missing from the target");
        }
        if (!balanceMismatches.isEmpty()) {
            balanceMismatches.forEach(
                    mismatch -> report.warning("Balance mismatch: " + mismatch)
            );
            throw new MigrationException("Customer balance reconciliation failed: "
                    + String.join("; ", balanceMismatches));
        }

        BigDecimal targetTotal = MoneyUtil.ZERO;
        try (Statement statement = target.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COALESCE(SUM(wallet_balance), 0) FROM customer")) {
            result.next();
            targetTotal = result.getBigDecimal(1).setScale(2);
        }
        report.targetTotalBalance(targetTotal);
        if (targetTotal.compareTo(report.getSourceTotalBalance()) != 0) {
            throw new MigrationException("Total wallet balance changed: source "
                    + report.getSourceTotalBalance().toPlainString() + ", target "
                    + targetTotal.toPlainString());
        }

        List<String> mismatches = new ArrayList<>();
        String sql = """
                SELECT c.id, c.wallet_balance, COALESCE(SUM(t.amount), 0) AS ledger_total
                FROM customer c
                LEFT JOIN wallet_transaction t ON t.customer_id = c.id
                GROUP BY c.id, c.wallet_balance
                HAVING c.wallet_balance <> COALESCE(SUM(t.amount), 0)
                ORDER BY c.id
                """;
        try (Statement statement = target.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                mismatches.add("customer " + result.getLong("id")
                        + " balance " + result.getBigDecimal("wallet_balance").toPlainString()
                        + " vs ledger " + result.getBigDecimal("ledger_total").toPlainString());
            }
        }
        report.ledgerMismatches(mismatches.size());
        if (!mismatches.isEmpty()) {
            mismatches.forEach(mismatch -> report.warning("Ledger mismatch: " + mismatch));
            throw new MigrationException("Ledger reconciliation failed: "
                    + String.join("; ", mismatches));
        }
    }

    private static String targetType(String sourceType) {
        return switch (sourceType.toLowerCase(Locale.ROOT)) {
            case "recharge" -> "TOPUP";
            case "checkin" -> "CHECKIN";
            default -> throw new MigrationException("Unknown transaction type: " + sourceType);
        };
    }

    private static String canonicalUuid(String value, String context) {
        String canonical = canonicalCode(value, context);
        try {
            UUID.fromString(canonical);
            return canonical;
        } catch (IllegalArgumentException exception) {
            throw new MigrationException("Invalid UUID for " + context + ": " + value);
        }
    }

    private static String canonicalCode(String value, String context) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new MigrationException("Missing identifier for " + context);
        }
        if (trimmed.matches("[0-9a-fA-F]{32}")) {
            return UUID.fromString(
                    trimmed.substring(0, 8)
                            + "-" + trimmed.substring(8, 12)
                            + "-" + trimmed.substring(12, 16)
                            + "-" + trimmed.substring(16, 20)
                            + "-" + trimmed.substring(20)
            ).toString();
        }
        try {
            return UUID.fromString(trimmed).toString();
        } catch (IllegalArgumentException exception) {
            return trimmed;
        }
    }

    private static void requireUnique(Set<Long> values, long value, String message) {
        if (!values.add(value)) {
            throw new MigrationException(message);
        }
    }

    private static String requireText(String value, String context) {
        if (value == null || value.isBlank()) {
            throw new MigrationException("Missing value for " + context);
        }
        return value;
    }

    private static void requireTargetMoney(BigDecimal value, BigDecimal maximum, String context) {
        if (value == null || value.signum() < 0 || value.compareTo(maximum) > 0) {
            throw new MigrationException("Money out of range for " + context + ": " + value);
        }
    }

    private static void setTimestamp(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
            return;
        }
        statement.setTimestamp(index, timestamp(value));
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value.truncatedTo(ChronoUnit.MILLIS));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String redact(String jdbcUrl) {
        return jdbcUrl.replaceAll("(?i)(password=)[^&]*", "$1***");
    }

    private record Ledger(List<LedgerRow> rows, List<CheckInRow> checkIns) {
    }

    private record LedgerRow(
            Long sourceTransactionId,
            long customerId,
            String type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String note,
            Instant createdAt
    ) {
    }

    private record CheckInRow(
            long customerId,
            long transactionId,
            BigDecimal fee,
            Instant checkedInAt
    ) {
    }
}
