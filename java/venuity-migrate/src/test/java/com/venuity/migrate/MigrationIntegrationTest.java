package com.venuity.migrate;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class MigrationIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("venuity")
            .withUsername("venuity")
            .withPassword("venuity-test");

    static {
        MYSQL.start();
    }

    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void migrateSchema() {
        String migrations = repositoryPath("java/venuity-server/src/main/resources/db/migration")
                .toAbsolutePath()
                .toString()
                .replace('\\', '/');
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + migrations)
                .load()
                .migrate();
    }

    @BeforeEach
    void clearTarget() throws SQLException {
        try (Connection connection = targetConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            statement.execute("TRUNCATE TABLE idempotency_record");
            statement.execute("TRUNCATE TABLE check_in");
            statement.execute("TRUNCATE TABLE wallet_transaction");
            statement.execute("TRUNCATE TABLE customer");
            statement.execute("TRUNCATE TABLE app_user");
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
            statement.executeUpdate("""
                    UPDATE business_settings
                    SET business_name = 'PlayArea Manager',
                        currency_symbol = '\u20b9',
                        checkin_fee = 100.00,
                        primary_color = '#6366f1',
                        primary_hover_color = '#4f46e5',
                        logo_url = '/logo.png',
                        app_title = 'Venuity'
                    WHERE id = 1
                    """);
        }
    }

    @Test
    void migratesSyntheticGoldenSampleAndReconcilesBalances() throws SQLException {
        MigrationReport report = new MigrationRunner(options(samplePath(), false, false)).run();

        assertTrue(report.isClean());
        assertEquals(3L, report.getSourceCounts().get(MigrationReport.USERS));
        assertEquals(12L, report.getSourceCounts().get(MigrationReport.CUSTOMERS));
        assertEquals(26L, report.getSourceCounts().get(MigrationReport.LEDGER_ROWS));
        assertEquals(8L, report.getSourceCounts().get(MigrationReport.CHECK_INS));
        assertEquals(new BigDecimal("1346749.99"), report.getSourceTotalBalance());
        assertEquals(0, report.getLedgerMismatches());
        assertTrue(report.getWarnings().stream()
                .anyMatch(warning -> warning.contains("clamped to 0.00")));

        try (Connection connection = targetConnection()) {
            assertEquals(3L, count(connection, "app_user"));
            assertEquals(12L, count(connection, "customer"));
            assertEquals(26L, count(connection, "wallet_transaction"));
            assertEquals(8L, count(connection, "check_in"));
            assertEquals(new BigDecimal("1346749.99"), scalar(connection,
                    "SELECT SUM(wallet_balance) FROM customer"));
            assertEquals(8L, count(connection, "wallet_transaction WHERE type = 'CHECKIN' AND amount < 0"));
            assertEquals(0L, count(connection, "wallet_transaction WHERE type = 'TOPUP' AND amount < 0"));

            assertEquals("ADMIN", scalarString(connection,
                    "SELECT role FROM app_user WHERE username = 'admin'"));
            assertEquals("1", scalarString(connection,
                    "SELECT enabled FROM app_user WHERE username = 'admin'"));
            assertEquals("0", scalarString(connection,
                    "SELECT enabled FROM app_user WHERE username = 'inactive'"));
            assertTrue(scalarString(connection,
                    "SELECT password_hash FROM app_user WHERE username = 'admin'")
                    .startsWith("pbkdf2_sha256$"));
            assertEquals("10000000-0000-4000-8000-000000000001", scalarString(connection,
                    "SELECT public_code FROM customer WHERE name = 'Asha Rao'"));
            assertEquals(Instant.parse("2026-04-01T04:30:00Z"), scalarTimestamp(connection,
                    "SELECT created_at FROM customer WHERE name = 'Asha Rao'"));
            assertEquals("Synthetic PlayArea", scalarString(connection,
                    "SELECT business_name FROM business_settings WHERE id = 1"));
            assertEquals(0L, count(connection, """
                    SELECT COUNT(*) FROM (
                        SELECT c.id, c.wallet_balance
                        FROM customer c
                        LEFT JOIN wallet_transaction t ON t.customer_id = c.id
                        GROUP BY c.id, c.wallet_balance
                        HAVING c.wallet_balance <> COALESCE(SUM(t.amount), 0)
                    ) mismatches
                    """));
        }
    }

    @Test
    void dryRunRollsBackEverything() throws SQLException {
        MigrationReport report = new MigrationRunner(options(samplePath(), false, true)).run();

        assertTrue(report.isClean());
        assertTrue(report.getWarnings().stream()
                .anyMatch(warning -> warning.contains("clamped to 0.00")));
        try (Connection connection = targetConnection()) {
            assertEquals(0L, count(connection, "app_user"));
            assertEquals(0L, count(connection, "customer"));
            assertEquals(0L, count(connection, "wallet_transaction"));
            assertEquals(0L, count(connection, "check_in"));
            assertEquals("PlayArea Manager", scalarString(connection,
                    "SELECT business_name FROM business_settings WHERE id = 1"));
        }
    }

    @Test
    void refusesToRunAgainstNonEmptyTarget() throws Exception {
        new MigrationRunner(options(samplePath(), false, false)).run();
        Path reportPath = temporaryDirectory.resolve("rerun-report.md");

        MigrationException exception = assertThrows(
                MigrationException.class,
                () -> new MigrationRunner(
                        options(samplePath(), false, false, reportPath)
                ).run()
        );
        assertTrue(exception.getMessage().contains("Target contains data"));
        assertTrue(Files.exists(reportPath));
        assertTrue(Files.readString(reportPath).contains("Result: FAILED"));
        try (Connection connection = targetConnection()) {
            assertEquals(3L, count(connection, "app_user"));
            assertEquals(12L, count(connection, "customer"));
            assertEquals(26L, count(connection, "wallet_transaction"));
        }
    }

    @Test
    void forceReplacesExistingTargetData() throws SQLException {
        new MigrationRunner(options(samplePath(), false, false)).run();
        Path edge = createEdgeFixture();

        MigrationReport dryRun = new MigrationRunner(options(edge, true, true)).run();
        assertTrue(dryRun.isClean());
        try (Connection connection = targetConnection()) {
            assertEquals(3L, count(connection, "app_user"));
            assertEquals(12L, count(connection, "customer"));
            assertEquals(1L, count(connection, "app_user WHERE username = 'admin'"));
            assertEquals("Synthetic PlayArea", scalarString(connection,
                    "SELECT business_name FROM business_settings WHERE id = 1"));
        }

        MigrationReport report = new MigrationRunner(options(edge, true, false)).run();

        assertTrue(report.isClean());
        assertTrue(report.getWarnings().stream()
                .anyMatch(warning -> warning.contains("Forced replacement")));
        try (Connection connection = targetConnection()) {
            assertEquals(1L, count(connection, "app_user"));
            assertEquals(6L, count(connection, "customer"));
            assertEquals(4L, count(connection, "wallet_transaction"));
            assertEquals(0L, count(connection, "check_in"));
            assertEquals(0L, count(connection, "app_user WHERE username = 'admin'"));
            assertEquals("edge", scalarString(connection,
                    "SELECT username FROM app_user"));
            assertEquals("Edge PlayArea", scalarString(connection,
                    "SELECT business_name FROM business_settings WHERE id = 1"));
        }
    }

    @Test
    void roundsScaleAndAddsOpeningAndAdjustmentRows() throws SQLException {
        Path edge = createEdgeFixture();
        MigrationReport report = new MigrationRunner(options(edge, false, false)).run();

        assertTrue(report.isClean());
        assertEquals(2, report.getOpeningBalances());
        assertEquals(1, report.getAdjustments().size());
        assertTrue(report.getAdjustments().getFirst().contains("-10.00"));
        assertTrue(report.getRoundedValues().stream()
                .anyMatch(value -> value.contains("12.345 -> 12.35")));
        assertEquals(0, report.getLedgerMismatches());

        try (Connection connection = targetConnection()) {
            assertEquals(6L, count(connection, "customer"));
            assertEquals(4L, count(connection, "wallet_transaction"));
            assertEquals(6L, count(connection, "customer WHERE phone IS NULL"));
            assertEquals(new BigDecimal("100000102.34"), scalar(connection,
                    "SELECT SUM(wallet_balance) FROM customer"));
            assertEquals(new BigDecimal("12.35"), scalar(connection,
                    "SELECT wallet_balance FROM customer WHERE name = 'Edge \u00dcnicode'"));
            assertEquals(new BigDecimal("99999999.99"), scalar(connection,
                    "SELECT wallet_balance FROM customer WHERE name = 'Huge Wallet'"));
            assertEquals(new BigDecimal("90.00"), scalar(connection,
                    "SELECT wallet_balance FROM customer WHERE name = 'Adjust Me'"));
            assertEquals(Instant.parse("2026-04-01T04:30:00.123Z"), scalarTimestamp(connection,
                    "SELECT created_at FROM customer WHERE name = 'Edge \u00dcnicode'"));
            assertEquals(0L, count(connection, """
                    SELECT COUNT(*) FROM (
                        SELECT c.id, c.wallet_balance
                        FROM customer c
                        LEFT JOIN wallet_transaction t ON t.customer_id = c.id
                        GROUP BY c.id, c.wallet_balance
                        HAVING c.wallet_balance <> COALESCE(SUM(t.amount), 0)
                    ) mismatches
                    """));
        }
        assertTrue(Files.exists(edge));
    }

    private Path createEdgeFixture() throws SQLException {
        Path database = temporaryDirectory.resolve("edge.sqlite3");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE users_user (
                        id INTEGER PRIMARY KEY,
                        public_id TEXT NOT NULL,
                        username TEXT NOT NULL,
                        password TEXT NOT NULL,
                        email TEXT,
                        first_name TEXT,
                        last_name TEXT,
                        is_superuser INTEGER NOT NULL,
                        is_staff INTEGER NOT NULL,
                        is_active INTEGER NOT NULL,
                        date_joined TEXT NOT NULL,
                        last_login TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE customers_customer (
                        id INTEGER PRIMARY KEY,
                        public_id TEXT NOT NULL,
                        qr_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        balance TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE transactions_transaction (
                        id INTEGER PRIMARY KEY,
                        customer_id INTEGER NOT NULL,
                        transaction_type TEXT NOT NULL,
                        amount TEXT NOT NULL,
                        notes TEXT,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE settings_app_applicationsetting (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO users_user VALUES (
                        1, '20000000000040008000000000000001', 'edge',
                        'pbkdf2_sha256$1500000$c2FsdA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=',
                        'edge@example.invalid', '', '', 0, 1, 1,
                        '2026-04-01 04:30:00.123456', NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO customers_customer VALUES
                        (1, '20000000000040008000000000000001',
                         '30000000000040008000000000000001', 'Edge \u00dcnicode',
                         '12.345', '2026-04-01 04:30:00.123456'),
                        (2, '20000000000040008000000000000002',
                         '30000000000040008000000000000002', 'Huge Wallet',
                         '99999999.99', '2026-04-01 04:30:00'),
                        (3, '20000000000040008000000000000003',
                         '30000000000040008000000000000003', 'Adjust Me',
                         '90.00', '2026-04-01 04:30:00'),
                        (4, '20000000000040008000000000000004',
                         '30000000000040008000000000000004', 'Zero Empty',
                         '0.00', '2026-04-01 04:30:00'),
                        (5, '20000000000040008000000000000005',
                         '30000000000040008000000000000005', 'Same Name',
                         '0.00', '2026-04-01 04:30:00'),
                        (6, '20000000000040008000000000000006',
                         '30000000000040008000000000000006', 'Same Name',
                         '0.00', '2026-04-01 04:30:00')
                    """);
            statement.execute("""
                    INSERT INTO transactions_transaction VALUES (
                        1, 3, 'recharge', '100.00', 'Edge top-up',
                        '2026-04-02 04:30:00'
                    )
                    """);
            statement.execute("""
                    INSERT INTO settings_app_applicationsetting VALUES
                        ('business_name', 'Edge PlayArea'),
                        ('currency_symbol', '\u20b9'),
                        ('checkin_fee', '150.00'),
                        ('primary_color', '#112233'),
                        ('primary_hover_color', '#445566'),
                        ('logo_url', '/edge.png'),
                        ('app_title', 'Edge')
                    """);
        }
        return database;
    }

    private MigrationOptions options(Path sqlite, boolean force, boolean dryRun) {
        return options(sqlite, force, dryRun, null);
    }

    private MigrationOptions options(
            Path sqlite,
            boolean force,
            boolean dryRun,
            Path reportPath
    ) {
        return new MigrationOptions(
                sqlite,
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword(),
                dryRun,
                force,
                ZoneId.of("UTC"),
                reportPath
        );
    }

    private static Connection targetConnection() throws SQLException {
        return DriverManager.getConnection(
                MYSQL.getJdbcUrl()
                        + "?connectionTimeZone=UTC&useUnicode=true&characterEncoding=UTF-8"
                        + "&allowPublicKeyRetrieval=true&useSSL=false",
                MYSQL.getUsername(),
                MYSQL.getPassword()
        );
    }

    private static long count(Connection connection, String tableOrCondition) throws SQLException {
        String query = tableOrCondition.strip();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     query.startsWith("SELECT")
                             ? query
                             : "SELECT COUNT(*) FROM " + query)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static BigDecimal scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getBigDecimal(1);
        }
    }

    private static String scalarString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private static Instant scalarTimestamp(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getTimestamp(1).toInstant();
        }
    }

    private static Path samplePath() {
        return repositoryPath("docs/migration/golden/sample.sqlite3");
    }

    private static Path repositoryPath(String relative) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate " + relative);
    }
}
