package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** FR-1: signup auto-provisions a virtual account. */
class OnboardingFlowIT extends AbstractIntegrationTest {

    @Test
    void signupAutoProvisionsVirtualAccount() {
        Map<String, Object> signupRequest = uniqueSignupPayload("Ada", "Lovelace", "SecurePass123");

        ResponseEntity<Map> signupResponse = restTemplate.postForEntity(url("/api/v1/auth/signup"), signupRequest, Map.class);
        assertThat(signupResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String accessToken = (String) signupResponse.getBody().get("accessToken");
        assertThat(accessToken).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<List> virtualAccounts = restTemplate.exchange(
                url("/api/v1/virtual-accounts"), HttpMethod.GET, new HttpEntity<>(headers), List.class);

        assertThat(virtualAccounts.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(virtualAccounts.getBody()).hasSize(1);
        Map<String, Object> account = (Map<String, Object>) virtualAccounts.getBody().get(0);
        assertThat(account.get("accountNumber")).isNotNull();
        assertThat(account.get("currency")).isEqualTo("NGN");
        assertThat(account.get("balance")).isEqualTo(0);
    }
}
