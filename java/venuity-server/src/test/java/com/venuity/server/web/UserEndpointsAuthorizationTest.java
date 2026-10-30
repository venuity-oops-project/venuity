package com.venuity.server.web;

import com.venuity.server.domain.AppUser;
import com.venuity.server.domain.Role;
import com.venuity.server.repo.AppUserRepository;
import com.venuity.server.security.JwtService;
import com.venuity.server.security.PasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserEndpointsAuthorizationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("venuity")
            .withUsername("venuity")
            .withPassword("venuity-test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?connectionTimeZone=UTC&sslMode=PREFERRED");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("venuity.jwt.secret", () -> "endpoint-auth-test-secret-32-bytes-minimum");
        registry.add("venuity.bootstrap.force-reset", () -> "false");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String staffToken;

    @BeforeEach
    void setUp() {
        AppUser admin = userRepository.findByUsernameIgnoreCase("auth_admin")
                .orElseGet(() -> {
                    AppUser u = AppUser.create("auth_admin", passwordService.encode("Admin#123456"), Role.ADMIN);
                    u.setFullName("Auth Admin");
                    u.setMustChangePassword(false);
                    return userRepository.save(u);
                });
        adminToken = jwtService.issue(admin).access();

        AppUser staff = userRepository.findByUsernameIgnoreCase("auth_staff")
                .orElseGet(() -> {
                    AppUser u = AppUser.create("auth_staff", passwordService.encode("Staff#123456"), Role.STAFF);
                    u.setFullName("Auth Staff");
                    u.setMustChangePassword(false);
                    return userRepository.save(u);
                });
        staffToken = jwtService.issue(staff).access();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("Anonymous requests to all user management, audit log, and me endpoints return 401")
    void testAnonymousRequestsReturn401() {
        List<EndpointCall> endpoints = List.of(
                new EndpointCall(HttpMethod.GET, "/api/users", null),
                new EndpointCall(HttpMethod.POST, "/api/users", "{}"),
                new EndpointCall(HttpMethod.GET, "/api/users/1", null),
                new EndpointCall(HttpMethod.PUT, "/api/users/1", "{}"),
                new EndpointCall(HttpMethod.DELETE, "/api/users/1", null),
                new EndpointCall(HttpMethod.POST, "/api/users/1/reset-password", "{}"),
                new EndpointCall(HttpMethod.POST, "/api/users/bulk", "{}"),
                new EndpointCall(HttpMethod.GET, "/api/users/export", null),
                new EndpointCall(HttpMethod.POST, "/api/users/import/preview", "{}"),
                new EndpointCall(HttpMethod.POST, "/api/users/import/commit", "{}"),
                new EndpointCall(HttpMethod.GET, "/api/audit-log", null),
                new EndpointCall(HttpMethod.GET, "/api/audit-log/export", null),
                new EndpointCall(HttpMethod.GET, "/api/me", null),
                new EndpointCall(HttpMethod.PUT, "/api/me", "{}"),
                new EndpointCall(HttpMethod.POST, "/api/me/change-password", "{}"),
                new EndpointCall(HttpMethod.GET, "/api/me/sessions", null),
                new EndpointCall(HttpMethod.DELETE, "/api/me/sessions/1", null),
                new EndpointCall(HttpMethod.POST, "/api/me/sessions/revoke-others", null)
        );

        HttpHeaders headers = authHeaders(null);
        for (EndpointCall ep : endpoints) {
            HttpEntity<?> entity = ep.body == null ? new HttpEntity<>(headers) : new HttpEntity<>(ep.body, headers);
            ResponseEntity<String> response = rest.exchange(ep.path, ep.method, entity, String.class);
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                    "Expected 401 for anonymous " + ep.method + " " + ep.path);
        }
    }

    @Test
    @DisplayName("STAFF requests to admin-only user management and audit endpoints return 403")
    void testStaffForbiddenOnAdminEndpoints() {
        List<EndpointCall> endpoints = List.of(
                new EndpointCall(HttpMethod.GET, "/api/users", null),
                new EndpointCall(HttpMethod.POST, "/api/users", "{\"username\":\"test\",\"password\":\"P@ss123456\"}"),
                new EndpointCall(HttpMethod.GET, "/api/users/1", null),
                new EndpointCall(HttpMethod.PUT, "/api/users/1", "{\"role\":\"STAFF\"}"),
                new EndpointCall(HttpMethod.DELETE, "/api/users/1", null),
                new EndpointCall(HttpMethod.POST, "/api/users/1/reset-password", "{\"temporaryPassword\":\"P@ss123456\"}"),
                new EndpointCall(HttpMethod.POST, "/api/users/bulk", "{\"action\":\"ENABLE\",\"userIds\":[1]}"),
                new EndpointCall(HttpMethod.GET, "/api/users/export", null),
                new EndpointCall(HttpMethod.POST, "/api/users/import/preview", "{\"csvData\":\"username,email,role\\ntest,test@test.com,STAFF\"}"),
                new EndpointCall(HttpMethod.POST, "/api/users/import/commit", "{\"rows\":[]}"),
                new EndpointCall(HttpMethod.GET, "/api/audit-log", null),
                new EndpointCall(HttpMethod.GET, "/api/audit-log/export", null)
        );

        HttpHeaders headers = authHeaders(staffToken);
        for (EndpointCall ep : endpoints) {
            HttpEntity<?> entity = ep.body == null ? new HttpEntity<>(headers) : new HttpEntity<>(ep.body, headers);
            ResponseEntity<String> response = rest.exchange(ep.path, ep.method, entity, String.class);
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                    "Expected 403 for STAFF " + ep.method + " " + ep.path);
        }
    }

    @Test
    @DisplayName("STAFF requests to /api/me and /api/me/sessions return 200")
    void testStaffCanAccessMeEndpoints() {
        HttpHeaders headers = authHeaders(staffToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<String> meResponse = rest.exchange("/api/me", HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.OK, meResponse.getStatusCode(), "Expected 200 for STAFF GET /api/me");

        ResponseEntity<String> sessionsResponse = rest.exchange("/api/me/sessions", HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.OK, sessionsResponse.getStatusCode(), "Expected 200 for STAFF GET /api/me/sessions");
    }

    @Test
    @DisplayName("ADMIN requests to /api/users, /api/audit-log, and /api/me return 200")
    void testAdminCanAccessAdminEndpoints() {
        HttpHeaders headers = authHeaders(adminToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<String> usersResponse = rest.exchange("/api/users", HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.OK, usersResponse.getStatusCode(), "Expected 200 for ADMIN GET /api/users");

        ResponseEntity<String> auditResponse = rest.exchange("/api/audit-log", HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.OK, auditResponse.getStatusCode(), "Expected 200 for ADMIN GET /api/audit-log");

        ResponseEntity<String> meResponse = rest.exchange("/api/me", HttpMethod.GET, entity, String.class);
        assertEquals(HttpStatus.OK, meResponse.getStatusCode(), "Expected 200 for ADMIN GET /api/me");
    }

    private static class EndpointCall {
        final HttpMethod method;
        final String path;
        final String body;

        EndpointCall(HttpMethod method, String path, String body) {
            this.method = method;
            this.path = path;
            this.body = body;
        }
    }
}
