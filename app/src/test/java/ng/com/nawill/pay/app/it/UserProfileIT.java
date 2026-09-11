package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** GET /api/v1/users/me - previously no profile-read endpoint existed at all. */
class UserProfileIT extends AbstractIntegrationTest {

    @Test
    void returnsCallersOwnProfileIncludingBusinessName() {
        Map<String, Object> signup = businessSignup("Ada", "Lovelace", "SecurePass123!");
        String token = (String) signup.get("accessToken");

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/users/me"), HttpMethod.GET, new HttpEntity<>(authHeaders(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("userId")).isEqualTo(signup.get("userId"));
        assertThat(response.getBody().get("businessId")).isEqualTo(signup.get("businessId"));
        assertThat(response.getBody().get("firstName")).isEqualTo("Ada");
        assertThat(response.getBody().get("businessName")).isNotNull();
    }

    @Test
    void requiresAuthentication() {
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/users/me"), HttpMethod.GET, new HttpEntity<>(new org.springframework.http.HttpHeaders()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
