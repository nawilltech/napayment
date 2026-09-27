package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Manual settlement trigger and real-time auto-settle-on-credit (FR-Settle-1). */
class SettlementServiceIT extends AbstractIntegrationTest {

    private String businessToken;
    private String virtualAccountId;
    private String paymentProcessorId;

    @BeforeEach
    void setUp() {
        Map<String, Object> signupBody = uniqueSignupPayload("Radia", "Perlman", "SecurePass123!");
        Map<String, Object> businessSignupBody = new java.util.HashMap<>(signupBody);
        businessSignupBody.put("businessName", "Perlman Settlement Co");
        businessSignupBody.put("cacNumber", "RC" + UUID.randomUUID().toString().substring(0, 6));
        ResponseEntity<Map> signup = restTemplate.postForEntity(url("/api/v1/auth/signup"), businessSignupBody, Map.class);
        businessToken = (String) signup.getBody().get("accessToken");

        List<Map<String, Object>> accounts = getPagedContent("/api/v1/virtual-accounts", authHeaders(businessToken));
        virtualAccountId = (String) accounts.get(0).get("id");

        String adminToken = superAdminToken();
        Map<String, Object> processorRequest = Map.of("name", "SandboxProcessor-" + UUID.randomUUID());
        ResponseEntity<Map> processorResponse = restTemplate.exchange(
                url("/api/v1/payment-processors"), HttpMethod.POST,
                new HttpEntity<>(processorRequest, authHeaders(adminToken)), Map.class);
        paymentProcessorId = (String) processorResponse.getBody().get("id");

        ensureCollectionAccountExists(adminToken);

        UUID bankId = insertBank();
        Map<String, Object> bankAccountRequest = Map.of(
                "bankId", bankId.toString(), "accountNumber", "1234567890", "accountName", "Perlman Settlement");
        ResponseEntity<Map> bankAccountResponse = restTemplate.exchange(
                url("/api/v1/bank-accounts"), HttpMethod.POST,
                new HttpEntity<>(bankAccountRequest, authHeaders(businessToken)), Map.class);
        String bankAccountId = (String) bankAccountResponse.getBody().get("id");

        restTemplate.exchange(url("/api/v1/settlement-accounts"), HttpMethod.POST,
                new HttpEntity<>(Map.of("bankAccountId", bankAccountId, "splitPercentage", new BigDecimal("100.00")),
                        authHeaders(businessToken)),
                Map.class);
    }

    @Test
    void manualSettleAfterACreditCompletesForTheFullAmount() {
        creditVirtualAccountViaTransactionsEndpoint(5000);

        var headers = authHeaders(businessToken);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<List> settle = restTemplate.exchange(
                url("/api/v1/settlements"), HttpMethod.POST, new HttpEntity<>(Map.of(), headers), List.class);

        assertThat(settle.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(settle.getBody()).hasSize(1);
        Map<String, Object> settlement = (Map<String, Object>) settle.getBody().get(0);
        assertThat(settlement.get("settlementStatus")).isEqualTo("COMPLETED");
        assertThat(((Number) settlement.get("amount")).longValue()).isEqualTo(5000L);
    }

    @Test
    void autoSettleFiresInRealTimeAfterAnApiKeySignedCollect() {
        restTemplate.exchange(url("/api/v1/settlement-accounts/auto-settle"), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("autoSettle", true), authHeaders(businessToken)), Void.class);

        ResponseEntity<Map> apiKeyResponse = restTemplate.exchange(
                url("/api/v1/api-keys"), HttpMethod.POST, new HttpEntity<>(authHeaders(businessToken)), Map.class);
        String publicKey = (String) apiKeyResponse.getBody().get("publicKey");
        String secretKey = (String) apiKeyResponse.getBody().get("secretKey");

        String body = "{\"amount\":4000}";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = hmacSha256Hex(secretKey, timestamp + "." + body);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Public-Key", publicKey);
        headers.set("X-Timestamp", timestamp);
        headers.set("X-Signature", signature);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        ResponseEntity<Map> collect = restTemplate.exchange(
                url("/api/v1/collect"), HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        assertThat(collect.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(collect.getBody().get("transactionStatus")).isEqualTo("PAID");

        List<Map<String, Object>> accounts = getPagedContent("/api/v1/virtual-accounts", authHeaders(businessToken));
        Map<String, Object> account = accounts.get(0);
        // Auto-settle at 100% split moves the full credited amount back out immediately.
        assertThat(((Number) account.get("balance")).longValue()).isZero();
    }

    private void creditVirtualAccountViaTransactionsEndpoint(long amount) {
        Map<String, Object> transactionRequest = Map.of(
                "virtualAccountId", virtualAccountId, "paymentProcessorId", paymentProcessorId,
                "transactionType", "CREDIT", "amount", amount);
        var headers = authHeaders(businessToken);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/transactions"), HttpMethod.POST, new HttpEntity<>(transactionRequest, headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private void ensureCollectionAccountExists(String adminToken) {
        ResponseEntity<Map> existing = restTemplate.exchange(
                url("/api/v1/collection-account"), HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), Map.class);
        if (existing.getStatusCode() == HttpStatus.OK) {
            return;
        }
        UUID bankId = insertBank();
        Map<String, Object> request = Map.of(
                "bankId", bankId.toString(), "accountNumber", "9000000001", "accountName", "Nawill Pay Pool");
        restTemplate.exchange(url("/api/v1/collection-account"), HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(adminToken)), Map.class);
    }

    private UUID insertBank() {
        UUID bankId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO banks (id, name, code) VALUES (?, ?, ?)",
                bankId, "Test Bank " + bankId, bankId.toString().substring(0, 8));
        return bankId;
    }

    private static String hmacSha256Hex(String secret, String canonicalString) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
