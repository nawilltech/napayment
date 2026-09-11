package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** FR-9 webhook URL config (on the business's API key) and contact/dispute routing settings. */
class BusinessSettingsIT extends AbstractIntegrationTest {

    @Test
    void webhookConfigRequiresAnApiKeyAndValidatesUrls() {
        String token = businessSignupAndGetToken("Ada", "Lovelace", "SecurePass123!");

        ResponseEntity<Map> beforeKey = restTemplate.exchange(
                url("/api/v1/api-keys/webhook-config"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("callbackUrl", "https://example.com/callback"), authHeaders(token)), Map.class);
        assertThat(beforeKey.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        restTemplate.exchange(url("/api/v1/api-keys"), HttpMethod.POST, new HttpEntity<>(authHeaders(token)), Map.class);

        ResponseEntity<Map> invalidUrl = restTemplate.exchange(
                url("/api/v1/api-keys/webhook-config"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("callbackUrl", "not-a-url"), authHeaders(token)), Map.class);
        assertThat(invalidUrl.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> saved = restTemplate.exchange(
                url("/api/v1/api-keys/webhook-config"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("callbackUrl", "https://example.com/callback",
                        "webhookUrl", "https://example.com/webhooks/nawill"), authHeaders(token)),
                Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody().get("webhookUrl")).isEqualTo("https://example.com/webhooks/nawill");

        ResponseEntity<Map> fetched = restTemplate.exchange(
                url("/api/v1/api-keys/webhook-config"), HttpMethod.GET, new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(fetched.getBody().get("callbackUrl")).isEqualTo("https://example.com/callback");
    }

    @Test
    void contactSettingsDefaultToAccountEmailThenPersistOverride() {
        Map<String, Object> signup = businessSignup("Grace", "Hopper", "SecurePass123!");
        String token = (String) signup.get("accessToken");

        ResponseEntity<Map> before = restTemplate.exchange(
                url("/api/v1/business/contact"), HttpMethod.GET, new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) before.getBody().get("generalEmail")).contains("@");

        ResponseEntity<Map> saved = restTemplate.exchange(
                url("/api/v1/business/contact"), HttpMethod.PUT,
                new HttpEntity<>(Map.of(
                        "disputeEmails", List.of("disputes@biz.com"),
                        "refundEmails", List.of(),
                        "supportEmail", "support@biz.com",
                        "generalEmail", "owner@biz.com"), authHeaders(token)),
                Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> after = restTemplate.exchange(
                url("/api/v1/business/contact"), HttpMethod.GET, new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(after.getBody().get("generalEmail")).isEqualTo("owner@biz.com");
        assertThat(after.getBody().get("disputeEmails")).isEqualTo(List.of("disputes@biz.com"));
    }
}
