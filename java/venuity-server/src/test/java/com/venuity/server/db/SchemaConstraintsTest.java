package com.venuity.server.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class SchemaConstraintsTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("venuity")
            .withUsername("venuity")
            .withPassword("venuity-test");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load()
                .migrate();
    }

    @Test
    void settingsRowIsSeeded() throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT business_name, currency_symbol, checkin_fee FROM business_settings")) {
            assertEquals(true, result.next());
            assertEquals("PlayArea Manager", result.getString("business_name"));
            assertEquals("₹", result.getString("currency_symbol"));
            assertEquals("100.00", result.getBigDecimal("checkin_fee").toPlainString());
            assertEquals(false, result.next());
        }
    }

    @Test
    void negativeWalletBalanceIsRejected() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO customer (public_id, public_code, name, wallet_balance) "
                            + "VALUES ('00000000-0000-4000-8000-0000000000f1', "
                            + "'10000000-0000-4000-8000-0000000000f1', 'Negative', -0.01)"));
        }
    }

    @Test
    void secondSettingsRowIsRejected() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO business_settings (id, business_name, currency_symbol, checkin_fee, "
                            + "primary_color, primary_hover_color, logo_url, app_title) VALUES "
                            + "(2, 'Second', '₹', 0.00, '#000000', '#111111', '/logo.png', 'Venuity')"));
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
