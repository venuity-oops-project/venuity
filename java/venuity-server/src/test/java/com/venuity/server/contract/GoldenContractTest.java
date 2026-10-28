package com.venuity.server.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GoldenContractTest {

    private static final List<String> REPLAY_ORDER = List.of(
            "analytics-hourly-checkins",
            "analytics-hourly-revenue",
            "analytics-invalid-date",
            "analytics-monthly-checkins",
            "analytics-weekly-revenue",
            "auth-login-success",
            "auth-login-bad-password",
            "auth-me-admin",
            "auth-me-unauthenticated",
            "auth-refresh",
            "settings-get-unauthenticated",
            "settings-branding-public",
            "settings-get-staff",
            "settings-update-invalid-colour",
            "settings-update-invalid-fee",
            "settings-update-staff-forbidden",
            "settings-update-admin",
            "customers-list-page1",
            "customers-search",
            "customers-search-none",
            "customers-detail",
            "customers-detail-not-found",
            "customers-qr-png",
            "transactions-list-page1",
            "transactions-filter-recharge",
            "transactions-filter-checkin",
            "transactions-stats",
            "customers-list-unauthenticated",
            "customers-create-empty-name",
            "customers-create",
            "customers-delete-staff-forbidden",
            "customers-delete-admin",
            "recharge-amount-too-small",
            "recharge-customer-not-found",
            "recharge-success",
            "recharge-zero-balance-customer",
            "checkin-missing-identifier",
            "checkin-invalid-qr",
            "checkin-insufficient",
            "checkin-success-qr",
            "checkin-by-public-id",
            "unknown-route"
    );

    private static final String GOLDEN_CREATED_PUBLIC_ID = "12ae31f0-f068-4b93-aed9-a54b8034cf75";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("venuity")
            .withUsername("venuity")
            .withPassword("venuity-test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> GoldenFixtureLoader.jdbcUrl(MYSQL.getJdbcUrl()));
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("venuity.jwt.secret", () -> "golden-contract-test-secret-32-bytes-minimum");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper mapper;

    private Path goldenDirectory;
    private Map<String, JsonNode> cases;
    private String adminToken;
    private String adminRefresh;
    private String staffToken;
    private String createdPublicId;

    @BeforeAll
    void setUp() throws Exception {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        goldenDirectory = repositoryPath("docs/migration/golden");
        GoldenFixtureLoader.load(goldenDirectory.resolve("sample.sqlite3"), MYSQL.getJdbcUrl(),
                MYSQL.getUsername(), MYSQL.getPassword());
        cases = readCases();
        assertEquals(new LinkedHashSet<>(REPLAY_ORDER), cases.keySet(),
                "Replay order must cover every golden fixture exactly once");

        JsonNode admin = login("admin", "Admin#12345");
        adminToken = admin.get("access").asText();
        adminRefresh = admin.get("refresh").asText();
        staffToken = login("staff", "Staff#12345").get("access").asText();
        assertLegacyPasswordsWereRehashed();
    }

    private void assertLegacyPasswordsWereRehashed() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                GoldenFixtureLoader.jdbcUrl(MYSQL.getJdbcUrl()),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        );
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT password_hash FROM app_user WHERE username IN ('admin', 'staff')"
             )) {
            int rehashed = 0;
            while (result.next()) {
                assertTrue(result.getString(1).startsWith("$2"));
                rehashed++;
            }
            assertEquals(2, rehashed);
        }
    }

    @Test
    void replaysGoldenContractInStatefulOrder() throws Exception {
        for (String name : REPLAY_ORDER) {
            replay(name, cases.get(name));
        }
        assertNotNull(createdPublicId, "customer creation should be replayed");
    }

    @Test
    void exposesOpenApiDocumentation() {
        ResponseEntity<String> docs = rest.getForEntity("/v3/api-docs", String.class);
        assertEquals(200, docs.getStatusCode().value());
        String spec = docs.getBody();
        assertNotNull(spec);
        for (String path : List.of("/api/customers", "/api/customers/{publicId}/recharge",
                "/api/checkins", "/api/v1/customers/recharge/")) {
            assertTrue(spec.contains("\"" + path + "\""), "OpenAPI missing " + path);
        }

        ResponseEntity<String> redirect = rest.getForEntity("/swagger-ui.html", String.class);
        assertTrue(redirect.getStatusCode().is3xxRedirection());
        assertNotNull(redirect.getHeaders().getLocation());
        assertTrue(redirect.getHeaders().getLocation().getPath().endsWith("/swagger-ui/index.html"));

        ResponseEntity<String> ui = rest.getForEntity("/swagger-ui/index.html", String.class);
        assertEquals(200, ui.getStatusCode().value());
        assertNotNull(ui.getBody());
        assertTrue(ui.getBody().toLowerCase(Locale.ROOT).contains("swagger ui"));
    }

    private void replay(String name, JsonNode goldenCase) throws Exception {
        JsonNode request = goldenCase.get("request");
        JsonNode expected = goldenCase.get("response");
        String method = request.get("method").asText();
        String path = request.get("path").asText();

        if (name.startsWith("customers-delete-") && createdPublicId != null) {
            path = path.replace(GOLDEN_CREATED_PUBLIC_ID, createdPublicId);
        }

        JsonNode body = request.get("body");
        if (body != null && !body.isNull() && name.equals("auth-refresh")) {
            ObjectNode refreshed = body.deepCopy();
            refreshed.put("refresh", adminRefresh);
            body = refreshed;
        }

        HttpHeaders headers = new HttpHeaders();
        String auth = request.get("auth").isNull() ? null : request.get("auth").asText();
        if ("admin".equals(auth)) {
            headers.setBearerAuth(adminToken);
        } else if ("staff".equals(auth)) {
            headers.setBearerAuth(staffToken);
        }
        if (body != null && !body.isNull()) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        HttpEntity<?> entity = body == null || body.isNull()
                ? new HttpEntity<>(headers)
                : new HttpEntity<>(body, headers);
        ResponseEntity<byte[]> response = rest.exchange(path, HttpMethod.valueOf(method), entity,
                byte[].class);

        int expectedStatus = name.equals("recharge-customer-not-found")
                ? 404
                : expected.get("status").asInt();
        assertEquals(expectedStatus, response.getStatusCode().value(), name);

        if (expectedStatus == 204 || name.equals("unknown-route")) {
            return;
        }

        byte[] actualBytes = response.getBody();
        assertNotNull(actualBytes, name);

        if (name.equals("customers-qr-png")) {
            assertQrPayload(actualBytes, expectedPayload());
            return;
        }

        JsonNode actual = mapper.readTree(actualBytes);
        if (name.equals("recharge-customer-not-found")) {
            assertEquals("not_found", actual.get("code").asText(), name);
            return;
        }
        if (name.equals("customers-create")) {
            createdPublicId = actual.get("public_id").asText();
        }

        String expectedContentType = expected.get("content_type").asText();
        assertTrue(response.getHeaders().getContentType() != null, name);
        assertTrue(response.getHeaders().getContentType().toString()
                .startsWith(expectedContentType), name);

        compare(name, expected.get("body"), actual, "$");
    }

    private void compare(String caseName, JsonNode expected, JsonNode actual, String path) {
        if (expected == null || expected.isNull()) {
            assertTrue(actual == null || actual.isNull(), path);
            return;
        }
        assertNotNull(actual, caseName + " " + path);
        if (expected.isObject()) {
            assertTrue(actual.isObject(), caseName + " " + path);
            for (var field : expected.properties()) {
                String childPath = path + "." + field.getKey();
                if (ignored(caseName, childPath, field.getKey())) {
                    continue;
                }
                assertTrue(actual.has(field.getKey()), caseName + " missing " + childPath);
                compare(caseName, field.getValue(), actual.get(field.getKey()), childPath);
            }
            assertEquals(expected.size(), actual.size(),
                    caseName + " unexpected fields at " + path);
        } else if (expected.isArray()) {
            assertTrue(actual.isArray(), caseName + " " + path);
            assertEquals(expected.size(), actual.size(), caseName + " " + path);
            for (int index = 0; index < expected.size(); index++) {
                compare(caseName, expected.get(index), actual.get(index),
                        path + "[" + index + "]");
            }
        } else if (expected.isTextual()) {
            assertTrue(actual.isTextual(), caseName + " " + path);
            String expectedText = expected.asText();
            String actualText = actual.asText();
            if (expectedText.equals("<redacted>")) {
                assertFalse(actualText.isBlank(), caseName + " " + path);
            } else if (fieldName(path).equals("next") || fieldName(path).equals("previous")) {
                assertEquals(normalizeLink(expectedText), normalizeLink(actualText),
                        caseName + " " + path);
            } else {
                assertEquals(normalizeTimestamp(expectedText), normalizeTimestamp(actualText),
                        caseName + " " + path);
            }
        } else {
            assertEquals(expected, actual, caseName + " " + path);
        }
    }

    private boolean ignored(String caseName, String path, String field) {
        if (field.equals("last_login")) {
            return true;
        }
        if (caseName.equals("customers-create")
                && (path.equals("$.public_id") || path.equals("$.created_at"))) {
            return true;
        }
        return caseName.equals("analytics-invalid-date") && field.equals("display_date");
    }

    private void assertQrPayload(byte[] png, String expectedPayload) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image, "QR response is not a readable PNG");
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        Result decoded = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source)));
        assertEquals(expectedPayload, decoded.getText());
    }

    private String expectedPayload() {
        return cases.get("customers-detail").get("response").get("body").get("qr_id").asText();
    }

    private JsonNode login(String username, String password) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request =
                new HttpEntity<>(Map.of("username", username, "password", password), headers);
        ResponseEntity<byte[]> response = rest.exchange("/api/v1/auth/token/", HttpMethod.POST,
                request, byte[].class);
        assertEquals(200, response.getStatusCode().value(), username + " login");
        return mapper.readTree(response.getBody());
    }

    private Map<String, JsonNode> readCases() throws IOException {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(goldenDirectory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonNode value = mapper.readTree(file.toFile());
                result.put(value.get("name").asText(), value);
            }
        }
        return result;
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

    private static String normalizeLink(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getRawPath() == null) {
                return value;
            }
            if (uri.getRawQuery() == null) {
                return uri.getRawPath();
            }
            List<String> query = new java.util.ArrayList<>(List.of(uri.getRawQuery().split("&")));
            query.sort(String::compareTo);
            return uri.getRawPath() + "?" + String.join("&", query);
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    private static String normalizeTimestamp(String value) {
        return value.replaceAll("(\\.\\d{1,9})(?=[+-]\\d{2}:\\d{2}$)", "");
    }

    private static String fieldName(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
