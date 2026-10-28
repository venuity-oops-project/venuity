package com.venuity.migrate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SourceDatabase {

    private static final Set<String> REQUIRED_TABLES = Set.of(
            "users_user",
            "customers_customer",
            "transactions_transaction",
            "settings_app_applicationsetting"
    );

    private SourceDatabase() {
    }

    static SourceData read(Path path, ZoneId timezone, MigrationReport report) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new MigrationException("SQLite database not found: " + path);
        }
        String url = "jdbc:sqlite:" + path.toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(url)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA query_only = ON");
            }
            verifyTables(connection);
            List<User> users = readUsers(connection, timezone);
            List<Customer> customers = readCustomers(connection, timezone, report);
            List<Transaction> transactions = readTransactions(connection, timezone, report);
            Map<String, String> settings = readSettings(connection);
            return new SourceData(users, customers, transactions, settings);
        } catch (SQLException exception) {
            throw new MigrationException("Could not read SQLite source " + path + ": "
                    + exception.getMessage(), exception);
        }
    }

    private static void verifyTables(Connection connection) throws SQLException {
        Set<String> actual = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type = 'table'")) {
            while (result.next()) {
                actual.add(result.getString(1));
            }
        }
        Set<String> missing = new HashSet<>(REQUIRED_TABLES);
        missing.removeAll(actual);
        if (!missing.isEmpty()) {
            throw new MigrationException("SQLite source is missing tables: " + missing);
        }
    }

    private static List<User> readUsers(Connection connection, ZoneId timezone) throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = """
                SELECT id, public_id, username, password, email, first_name, last_name,
                       is_superuser, is_staff, is_active, date_joined, last_login
                FROM users_user
                ORDER BY id
                """;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                String publicId = result.getString("public_id");
                String username = result.getString("username");
                String password = result.getString("password");
                Instant dateJoined = timestamp(
                        result.getString("date_joined"),
                        timezone,
                        "users_user.date_joined for " + username
                );
                users.add(new User(
                        result.getLong("id"),
                        publicId,
                        username,
                        password,
                        result.getString("email"),
                        result.getString("first_name"),
                        result.getString("last_name"),
                        result.getInt("is_superuser") != 0,
                        result.getInt("is_staff") != 0,
                        result.getInt("is_active") != 0,
                        timestamp(
                                result.getString("last_login"),
                                timezone,
                                "users_user.last_login for " + username
                        ),
                        dateJoined
                ));
            }
        }
        return users;
    }

    private static List<Customer> readCustomers(
            Connection connection,
            ZoneId timezone,
            MigrationReport report
    ) throws SQLException {
        List<Customer> customers = new ArrayList<>();
        String sql = """
                SELECT id, public_id, qr_id, name, balance, created_at
                FROM customers_customer
                ORDER BY id
                """;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                String name = result.getString("name");
                Instant createdAt = timestamp(
                        result.getString("created_at"),
                        timezone,
                        "customers_customer.created_at for " + name
                );
                customers.add(new Customer(
                        result.getLong("id"),
                        result.getString("public_id"),
                        result.getString("qr_id"),
                        name,
                        money(
                                result.getString("balance"),
                                "customers_customer.balance for " + name,
                                report
                        ),
                        createdAt
                ));
            }
        }
        return customers;
    }

    private static List<Transaction> readTransactions(
            Connection connection,
            ZoneId timezone,
            MigrationReport report
    ) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        String sql = """
                SELECT id, customer_id, transaction_type, amount, notes, created_at
                FROM transactions_transaction
                ORDER BY id
                """;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                String type = result.getString("transaction_type");
                Instant createdAt = timestamp(
                        result.getString("created_at"),
                        timezone,
                        "transactions_transaction.created_at for id " + result.getLong("id")
                );
                transactions.add(new Transaction(
                        result.getLong("id"),
                        result.getLong("customer_id"),
                        type,
                        money(
                                result.getString("amount"),
                                "transactions_transaction.amount for id " + result.getLong("id"),
                                report
                        ),
                        result.getString("notes"),
                        createdAt
                ));
            }
        }
        return transactions;
    }

    private static Map<String, String> readSettings(Connection connection) throws SQLException {
        Map<String, String> settings = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT key, value FROM settings_app_applicationsetting ORDER BY key")) {
            while (result.next()) {
                String value = result.getString("value");
                settings.put(result.getString("key"), value == null ? "" : value);
            }
        }
        return settings;
    }

    static BigDecimal money(String value, String context, MigrationReport report) {
        if (value == null || value.isBlank()) {
            throw new MigrationException("Missing money value: " + context);
        }
        try {
            BigDecimal raw = new BigDecimal(value.trim());
            BigDecimal normalized = raw.setScale(2, RoundingMode.HALF_UP);
            if (raw.compareTo(normalized) != 0) {
                report.roundedValue(context + ": "
                        + raw.toPlainString() + " -> " + normalized.toPlainString());
            }
            return normalized;
        } catch (NumberFormatException exception) {
            throw new MigrationException("Invalid money value for " + context + ": " + value);
        }
    }

    private static Instant timestamp(String value, ZoneId timezone, String context) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace(' ', 'T');
        try {
            return Instant.parse(normalized);
        } catch (RuntimeException ignored) {
            try {
                return OffsetDateTime.parse(normalized).toInstant();
            } catch (RuntimeException ignoredAgain) {
                try {
                    return LocalDateTime.parse(normalized).atZone(timezone).toInstant();
                } catch (RuntimeException exception) {
                    throw new MigrationException("Invalid timestamp for " + context + ": " + value);
                }
            }
        }
    }

    record SourceData(
            List<User> users,
            List<Customer> customers,
            List<Transaction> transactions,
            Map<String, String> settings
    ) {
    }

    record User(
            long id,
            String publicId,
            String username,
            String passwordHash,
            String email,
            String firstName,
            String lastName,
            boolean superuser,
            boolean staff,
            boolean active,
            Instant lastLogin,
            Instant dateJoined
    ) {
    }

    record Customer(
            long id,
            String publicId,
            String qrId,
            String name,
            BigDecimal balance,
            Instant createdAt
    ) {
    }

    record Transaction(
            long id,
            long customerId,
            String type,
            BigDecimal amount,
            String note,
            Instant createdAt
    ) {
    }
}
