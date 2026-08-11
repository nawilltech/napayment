package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Doc 3 §2.2 permission-string RBAC + doc 3 §7 requestId correlation: a
 * USER-role token cannot reach an admin-only endpoint, and every failed-auth
 * attempt is traceable in the structured logs via requestId.
 */
class RbacIT extends AbstractIntegrationTest {

    @Test
    void userRoleTokenIsRejectedFromAdminOnlyEndpoint() {
        String userToken = signupAndGetToken("Rosalind", "Franklin", "SecurePass123");

        Map<String, Object> processorRequest = Map.of("name", "Interswitch-" + UUID.randomUUID());
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/payment-processors"), HttpMethod.POST,
                new HttpEntity<>(processorRequest, authHeaders(userToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("errorCode")).isEqualTo("FORBIDDEN");
    }

    @Test
    void requestIdIsPresentInStructuredLogsForAFailedAuthAttempt(LogCapture logCapture) {
        Map<String, Object> signupRequest = uniqueSignupPayload("Marie", "Curie", "SecurePass123");
        restTemplate.postForEntity(url("/api/v1/auth/signup"), signupRequest, Map.class);

        Map<String, Object> badLogin = Map.of("email", signupRequest.get("email"), "password", "TotallyWrongPassword");
        ResponseEntity<Map> response = restTemplate.postForEntity(url("/api/v1/auth/login"), badLogin, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String requestId = response.getHeaders().getFirst("X-Request-Id");
        assertThat(requestId).isNotBlank();
        assertThat(logCapture.anyEventHasMdcValue("requestId", requestId)).isTrue();
    }
}
