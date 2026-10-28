package com.venuity.server.load;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnabledIfSystemProperty(named = "loadTest", matches = "true")
class CheckInLoadTest {

    private static final String ADMIN_USERNAME = "load-test-admin";
    private static final String ADMIN_PASSWORD = "LoadTest#12345";
    private static final int CONCURRENCY = 20;
    private static final int ROUNDS_PER_CUSTOMER = 5;

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
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?connectionTimeZone=UTC&useUnicode=true&characterEncoding=UTF-8"
                + "&allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("venuity.bootstrap.admin-username", () -> ADMIN_USERNAME);
        registry.add("venuity.bootstrap.admin-password", () -> ADMIN_PASSWORD);
        registry.add("venuity.jwt.secret", () -> "load-test-secret-at-least-32-bytes-long");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper mapper;

    private HttpClient client;
    private String token;
    private BigDecimal fee;

    @Test
    void twentyConcurrentCheckInsRemainCorrectAndMeetManyCustomerLatencyTarget() throws Exception {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        token = login();

        HttpResponse<String> settings = send("GET", "/api/settings", null, null);
        assertEquals(200, settings.statusCode());
        fee = new BigDecimal(mapper.readTree(settings.body()).get("checkinFee").asText());

        warmUp();

        String singleCode = createCustomer("Load single", new BigDecimal("100000.00"));
        List<Long> singleLatencies = runCheckIns(
                java.util.Collections.nCopies(CONCURRENCY, singleCode));
        long singleP95 = percentile95(singleLatencies);

        JsonNode single = getCustomerByCode(singleCode);
        BigDecimal expectedSingle = new BigDecimal("100000.00").subtract(
                fee.multiply(BigDecimal.valueOf(CONCURRENCY)));
        assertEquals(0, single.get("walletBalance").decimalValue().compareTo(expectedSingle));

        List<String> manyCodes = new ArrayList<>();
        for (int index = 0; index < CONCURRENCY; index++) {
            manyCodes.add(createCustomer("Load many " + index,
                    fee.multiply(BigDecimal.valueOf(ROUNDS_PER_CUSTOMER))));
        }
        List<String> manyRequests = new ArrayList<>();
        for (int round = 0; round < ROUNDS_PER_CUSTOMER; round++) {
            manyRequests.addAll(manyCodes);
        }
        List<Long> manyLatencies = runCheckIns(manyRequests);
        long manyP95 = percentile95(manyLatencies);

        for (String code : manyCodes) {
            JsonNode customer = getCustomerByCode(code);
            assertEquals(0, customer.get("walletBalance").decimalValue().compareTo(BigDecimal.ZERO));
        }

        System.out.println("LOAD_TEST single_customer p95_ms=" + singleP95
                + " samples=" + singleLatencies);
        System.out.println("LOAD_TEST many_customers p95_ms=" + manyP95
                + " samples=" + manyLatencies);

        assertTrue(singleP95 < 1500, "single-customer p95 was " + singleP95 + " ms");
        assertTrue(manyP95 < 200, "many-customer p95 was " + manyP95 + " ms");
    }

    private void warmUp() throws Exception {
        String code = createCustomer("Load warm-up", new BigDecimal("10000.00"));
        for (int index = 0; index < 5; index++) {
            HttpResponse<String> response = checkIn(code);
            assertEquals(200, response.statusCode(), response.body());
        }
    }

    private List<Long> runCheckIns(List<String> codes) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        try {
            List<Callable<Long>> tasks = codes.stream()
                    .map(code -> (Callable<Long>) () -> {
                        long started = System.nanoTime();
                        HttpResponse<String> response = checkIn(code);
                        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                        assertEquals(200, response.statusCode(), response.body());
                        return elapsed;
                    })
                    .toList();
            List<Future<Long>> futures = pool.invokeAll(tasks, 30, TimeUnit.SECONDS);
            List<Long> latencies = new ArrayList<>();
            for (Future<Long> future : futures) {
                assertTrue(!future.isCancelled(), "check-in timed out");
                latencies.add(future.get());
            }
            return latencies;
        } finally {
            pool.shutdownNow();
        }
    }

    private String login() throws Exception {
        ObjectNode body = mapper.createObjectNode()
                .put("username", ADMIN_USERNAME)
                .put("password", ADMIN_PASSWORD);
        HttpResponse<String> response = send("POST", "/api/auth/login", body.toString(), null);
        assertEquals(200, response.statusCode(), response.body());
        return mapper.readTree(response.body()).get("accessToken").asText();
    }

    private String createCustomer(String name, BigDecimal initialBalance) throws Exception {
        ObjectNode body = mapper.createObjectNode()
                .put("name", name)
                .put("initialBalance", initialBalance);
        HttpResponse<String> response = send("POST", "/api/customers", body.toString(), null);
        assertEquals(201, response.statusCode(), response.body());
        return mapper.readTree(response.body()).get("publicCode").asText();
    }

    private HttpResponse<String> checkIn(String code) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("code", code);
        return send("POST", "/api/checkins", body.toString(), UUID.randomUUID().toString());
    }

    private JsonNode getCustomerByCode(String code) throws Exception {
        HttpResponse<String> response = send("GET", "/api/customers/by-code/" + code, null, null);
        assertEquals(200, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String path, String body, String idempotencyKey)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static long percentile95(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        return sorted.get(Math.max(0, index));
    }
}
