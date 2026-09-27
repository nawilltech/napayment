package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Nawill Pay's single pooled collection account (FR-Settle-2, doc 3 §2.5) -
 * admin-only, singleton. Note: the collection account is a genuine
 * platform-wide singleton and Testcontainers here share one Postgres across
 * the whole test run (see AbstractIntegrationTest javadoc), so these tests
 * observe current state first rather than assuming they run before any
 * other IT class that might also touch this table.
 */
class CollectionAccountIT extends AbstractIntegrationTest {

    @Test
    void businessOwnerCannotCreateCollectionAccount() {
        Map<String, Object> signupBody = uniqueSignupPayload("Business", "Owner", "SecurePass123!");
        Map<String, Object> businessSignupBody = new java.util.HashMap<>(signupBody);
        businessSignupBody.put("businessName", "Locked Out Ventures");
        businessSignupBody.put("cacNumber", "RC" + UUID.randomUUID().toString().substring(0, 6));
        ResponseEntity<Map> signup = restTemplate.postForEntity(url("/api/v1/auth/signup"), businessSignupBody, Map.class);
        String token = (String) signup.getBody().get("accessToken");

        UUID bankId = insertBank();
        Map<String, Object> request = Map.of(
                "bankId", bankId.toString(), "accountNumber", "9000000099");
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/collection-account"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("errorCode")).isEqualTo(ErrorCode.FORBIDDEN.name());
    }

    @Test
    void singletonIsEnforcedForSuperAdmin() {
        String adminToken = superAdminToken();
        UUID bankId = insertBank();
        Map<String, Object> request = Map.of(
                "bankId", bankId.toString(), "accountNumber", "9000000001");

        ResponseEntity<Map> existing = restTemplate.exchange(
                url("/api/v1/collection-account"), HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), Map.class);

        if (existing.getStatusCode() == HttpStatus.OK) {
            // Another test already created the singleton this run - confirm a second create is rejected.
            ResponseEntity<Map> duplicate = restTemplate.exchange(
                    url("/api/v1/collection-account"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(adminToken)), Map.class);
            assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(duplicate.getBody().get("errorCode")).isEqualTo(ErrorCode.COLLECTION_ACCOUNT_ALREADY_ACTIVE.name());
            return;
        }

        ResponseEntity<Map> created = restTemplate.exchange(
                url("/api/v1/collection-account"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(adminToken)), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Number) created.getBody().get("balance")).longValue()).isZero();
        assertThat(created.getBody().get("accountName")).isEqualTo("TEST ACCOUNT HOLDER");

        ResponseEntity<Map> duplicate = restTemplate.exchange(
                url("/api/v1/collection-account"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(adminToken)), Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("errorCode")).isEqualTo(ErrorCode.COLLECTION_ACCOUNT_ALREADY_ACTIVE.name());
    }

    private UUID insertBank() {
        UUID bankId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO banks (id, name, code) VALUES (?, ?, ?)",
                bankId, "Test Bank " + bankId, bankId.toString().substring(0, 8));
        return bankId;
    }
}
