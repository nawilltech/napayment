package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** Settlement bank accounts + split-percentage configuration (FR-2, FR-Settle-1). */
class SettlementAccountIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsBankAccountAndSettlementAccountAtFullSplit() {
        String token = businessSignupToken("Grace", "Hopper");
        UUID bankId = insertBank();

        Map<String, Object> bankAccountRequest = Map.of(
                "bankId", bankId.toString(), "accountNumber", "1234567890", "accountName", "Grace Ventures Settlement");
        ResponseEntity<Map> bankAccountResponse = restTemplate.exchange(
                url("/api/v1/bank-accounts"), HttpMethod.POST, new HttpEntity<>(bankAccountRequest, authHeaders(token)), Map.class);
        assertThat(bankAccountResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String bankAccountId = (String) bankAccountResponse.getBody().get("id");

        Map<String, Object> settlementRequest = Map.of("bankAccountId", bankAccountId, "splitPercentage", new BigDecimal("100.00"));
        ResponseEntity<Map> settlementResponse = restTemplate.exchange(
                url("/api/v1/settlement-accounts"), HttpMethod.POST,
                new HttpEntity<>(settlementRequest, authHeaders(token)), Map.class);

        assertThat(settlementResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(settlementResponse.getBody().get("bankAccountId")).isEqualTo(bankAccountId);
        assertThat(((Number) settlementResponse.getBody().get("splitPercentage")).doubleValue()).isEqualTo(100.0);
    }

    @Test
    void splitPercentageExceeding100PercentIsRejected() {
        String token = businessSignupToken("Katherine", "Johnson");
        UUID bankId = insertBank();
        String bankAccountId = createBankAccount(token, bankId);

        ResponseEntity<Map> first = restTemplate.exchange(
                url("/api/v1/settlement-accounts"), HttpMethod.POST,
                new HttpEntity<>(Map.of("bankAccountId", bankAccountId, "splitPercentage", new BigDecimal("60.00")), authHeaders(token)),
                Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> second = restTemplate.exchange(
                url("/api/v1/settlement-accounts"), HttpMethod.POST,
                new HttpEntity<>(Map.of("bankAccountId", bankAccountId, "splitPercentage", new BigDecimal("50.00")), authHeaders(token)),
                Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(second.getBody().get("errorCode")).isEqualTo("SPLIT_PERCENTAGE_EXCEEDED");
    }

    @Test
    void autoSettleTogglesAndListReflectsCreatedAccounts() {
        String token = businessSignupToken("Ada", "Lovelace");
        UUID bankId = insertBank();
        String bankAccountId = createBankAccount(token, bankId);
        restTemplate.exchange(url("/api/v1/settlement-accounts"), HttpMethod.POST,
                new HttpEntity<>(Map.of("bankAccountId", bankAccountId, "splitPercentage", new BigDecimal("100.00")), authHeaders(token)),
                Map.class);

        ResponseEntity<Void> toggle = restTemplate.exchange(
                url("/api/v1/settlement-accounts/auto-settle"), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("autoSettle", true), authHeaders(token)), Void.class);
        assertThat(toggle.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        List<Map<String, Object>> list = getPagedContent("/api/v1/settlement-accounts", authHeaders(token));
        assertThat(list).hasSize(1);
    }

    private String businessSignupToken(String firstName, String lastName) {
        Map<String, Object> signupBody = uniqueSignupPayload(firstName, lastName, "SecurePass123!");
        Map<String, Object> businessSignupBody = new java.util.HashMap<>(signupBody);
        businessSignupBody.put("businessName", firstName + " Ventures");
        businessSignupBody.put("cacNumber", "RC" + UUID.randomUUID().toString().substring(0, 6));
        ResponseEntity<Map> response = restTemplate.postForEntity(url("/api/v1/auth/signup"), businessSignupBody, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("businessId")).isNotNull();
        return (String) response.getBody().get("accessToken");
    }

    private String createBankAccount(String token, UUID bankId) {
        Map<String, Object> request = Map.of(
                "bankId", bankId.toString(), "accountNumber", "1234567890", "accountName", "Test Settlement Account");
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/bank-accounts"), HttpMethod.POST, new HttpEntity<>(request, authHeaders(token)), Map.class);
        return (String) response.getBody().get("id");
    }

    private UUID insertBank() {
        UUID bankId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO banks (id, name, code) VALUES (?, ?, ?)",
                bankId, "Test Bank " + bankId, bankId.toString().substring(0, 8));
        return bankId;
    }
}
