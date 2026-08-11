package ng.com.nawill.pay.app.it;

import java.util.Map;
import java.util.UUID;
import ng.com.nawill.pay.app.NawillPayApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real Postgres + Redis via Testcontainers (doc 3 §5.2 - never H2/embedded).
 * <p>
 * Containers are started once in a static initializer (the Testcontainers
 * "singleton container" pattern) rather than via {@code @Testcontainers}/
 * {@code @Container}: that JUnit5 extension re-processes container fields
 * per concrete test class, which - observed empirically - started a fresh
 * Postgres + Redis pair (and a fresh Spring context) for every *IT class
 * instead of sharing one across the run. Manual start here guarantees
 * exactly one pair for the whole suite; Testcontainers' Ryuk reaper cleans
 * them up at JVM exit regardless of how they were started.
 */
@SpringBootTest(classes = NawillPayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(LogCaptureExtension.class)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    static final GenericContainer<?> REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("nawill_pay_test")
                .withUsername("nawill_test")
                .withPassword("nawill_test");
        POSTGRES.start();

        REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        REDIS.start();
    }

    static final String SUPERADMIN_EMAIL = "superadmin@test.nawill.com.ng";
    static final String SUPERADMIN_PASSWORD = "TestSuperAdmin123!";

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("nawill.auth.jwt.secret", () -> "integration-test-jwt-signing-secret-at-least-32-bytes");
        registry.add("nawill.auth.superadmin.email", () -> SUPERADMIN_EMAIL);
        registry.add("nawill.auth.superadmin.password", () -> SUPERADMIN_PASSWORD);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    /**
     * The JDK's default HttpURLConnection-backed request factory throws
     * "cannot retry due to server authentication, in streaming mode" on a
     * 401 response to a POST with a body - a known JDK HttpURLConnection
     * limitation unrelated to request buffering. Apache HttpClient5 doesn't
     * have this issue.
     */
    @BeforeEach
    void useApacheHttpClient() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** A unique-enough signup payload so parallel/repeated tests never collide on email/phoneNo. */
    protected static Map<String, Object> uniqueSignupPayload(String firstName, String lastName, String password) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return Map.of(
                "firstName", firstName,
                "lastName", lastName,
                "email", firstName.toLowerCase() + "-" + suffix + "@example.com",
                "phoneNo", String.format("0801%06d", Math.abs(suffix.hashCode()) % 1_000_000),
                "password", password
        );
    }

    protected String signupAndGetToken(String firstName, String lastName, String password) {
        Map<String, Object> body = uniqueSignupPayload(firstName, lastName, password);
        return (String) restTemplate.postForEntity(url("/api/v1/auth/signup"), body, Map.class).getBody().get("accessToken");
    }

    protected String loginAndGetToken(String email, String password) {
        Map<String, Object> body = Map.of("email", email, "password", password);
        return (String) restTemplate.postForEntity(url("/api/v1/auth/login"), body, Map.class).getBody().get("accessToken");
    }

    protected String superAdminToken() {
        return loginAndGetToken(SUPERADMIN_EMAIL, SUPERADMIN_PASSWORD);
    }

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
