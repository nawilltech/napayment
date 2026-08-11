package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Login issues a JWT; a protected endpoint rejects requests without one. */
class AuthFlowIT extends AbstractIntegrationTest {

    @Test
    void loginIssuesJwtAndProtectedEndpointRejectsWithoutIt() {
        Map<String, Object> signupRequest = uniqueSignupPayload("Grace", "Hopper", "SecurePass123");
        restTemplate.postForEntity(url("/api/v1/auth/signup"), signupRequest, Map.class);

        Map<String, Object> loginRequest = Map.of(
                "email", signupRequest.get("email"),
                "password", signupRequest.get("password")
        );
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(url("/api/v1/auth/login"), loginRequest, Map.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody().get("accessToken")).isNotNull();
        assertThat(loginResponse.getBody().get("tokenType")).isEqualTo("Bearer");

        ResponseEntity<Map> unauthenticatedResponse =
                restTemplate.getForEntity(url("/api/v1/virtual-accounts"), Map.class);
        assertThat(unauthenticatedResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginWithWrongPasswordIsRejected() {
        Map<String, Object> signupRequest = uniqueSignupPayload("Grace", "Hopper", "SecurePass123");
        restTemplate.postForEntity(url("/api/v1/auth/signup"), signupRequest, Map.class);

        Map<String, Object> badLogin = Map.of("email", signupRequest.get("email"), "password", "WrongPassword");
        ResponseEntity<Map> response = restTemplate.postForEntity(url("/api/v1/auth/login"), badLogin, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
