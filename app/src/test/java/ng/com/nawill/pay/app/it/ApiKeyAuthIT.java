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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** HMAC-SHA256 request signing for third-party API-key auth (FR-9, doc 3 §2.5). */
class ApiKeyAuthIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String businessToken;
    private String publicKey;
    private String secretKey;

    @BeforeEach
    void setUp() {
        Map<String, Object> signupBody = uniqueSignupPayload("Hedy", "Lamarr", "SecurePass123!");
        Map<String, Object> businessSignupBody = new java.util.HashMap<>(signupBody);
        businessSignupBody.put("businessName", "Lamarr Frequency Co");
        businessSignupBody.put("cacNumber", "RC" + UUID.randomUUID().toString().substring(0, 6));
        ResponseEntity<Map> signup = restTemplate.postForEntity(url("/api/v1/auth/signup"), businessSignupBody, Map.class);
        businessToken = (String) signup.getBody().get("accessToken");

        ResponseEntity<Map> apiKeyResponse = restTemplate.exchange(
                url("/api/v1/api-keys"), HttpMethod.POST, new HttpEntity<>(authHeaders(businessToken)), Map.class);
        assertThat(apiKeyResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        publicKey = (String) apiKeyResponse.getBody().get("publicKey");
        secretKey = (String) apiKeyResponse.getBody().get("secretKey");
    }

    @Test
    void generatingASecondKeyWhileOneIsActiveIsRejected() {
        ResponseEntity<Map> second = restTemplate.exchange(
                url("/api/v1/api-keys"), HttpMethod.POST, new HttpEntity<>(authHeaders(businessToken)), Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(second.getBody().get("errorCode")).isEqualTo("API_KEY_ALREADY_EXISTS");
    }

    @Test
    void regenerateIssuesANewPairAndInvalidatesTheOld() {
        ResponseEntity<Map> regenerated = restTemplate.exchange(
                url("/api/v1/api-keys/regenerate"), HttpMethod.POST, new HttpEntity<>(authHeaders(businessToken)), Map.class);
        assertThat(regenerated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String newPublicKey = (String) regenerated.getBody().get("publicKey");
        assertThat(newPublicKey).isNotEqualTo(publicKey);

        String body = "{\"amount\":1000}";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String oldSignature = hmacSha256Hex(secretKey, timestamp + "." + body);
        ResponseEntity<Map> withOldKey = signedCollect(publicKey, timestamp, oldSignature, body);
        assertThat(withOldKey.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(withOldKey.getBody().get("errorCode")).isEqualTo("INVALID_API_KEY");
    }

    @Test
    void validSignedCollectSucceeds() {
        String body = "{\"amount\":5000}";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = hmacSha256Hex(secretKey, timestamp + "." + body);

        ResponseEntity<Map> response = signedCollect(publicKey, timestamp, signature, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("transactionStatus")).isEqualTo("PAID");
        assertThat(response.getBody().get("transactionType")).isEqualTo("CREDIT");
    }

    @Test
    void missingSignatureHeadersAreRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/collect"), HttpMethod.POST, new HttpEntity<>("{\"amount\":1000}", headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("errorCode")).isEqualTo("MISSING_SIGNATURE_HEADERS");
    }

    @Test
    void invalidSignatureIsRejected() {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        ResponseEntity<Map> response = signedCollect(publicKey, timestamp, "0000deadbeef0000", "{\"amount\":1000}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("errorCode")).isEqualTo("INVALID_SIGNATURE");
    }

    @Test
    void staleTimestampIsRejected() {
        String body = "{\"amount\":1000}";
        String staleTimestamp = String.valueOf((System.currentTimeMillis() / 1000) - 600);
        String signature = hmacSha256Hex(secretKey, staleTimestamp + "." + body);

        ResponseEntity<Map> response = signedCollect(publicKey, staleTimestamp, signature, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("errorCode")).isEqualTo("STALE_TIMESTAMP");
    }

    @Test
    void ipWhitelistBlocksThenAllowsAfterRemoval() {
        ResponseEntity<Void> addWhitelist = restTemplate.exchange(
                url("/api/v1/api-keys/ip-whitelist"), HttpMethod.POST,
                new HttpEntity<>(Map.of("cidr", "10.0.0.0/8"), authHeaders(businessToken)), Void.class);
        assertThat(addWhitelist.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String body = "{\"amount\":1000}";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = hmacSha256Hex(secretKey, timestamp + "." + body);
        ResponseEntity<Map> blocked = signedCollect(publicKey, timestamp, signature, body);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(blocked.getBody().get("errorCode")).isEqualTo("IP_NOT_WHITELISTED");

        ResponseEntity<Void> removeWhitelist = restTemplate.exchange(
                url("/api/v1/api-keys/ip-whitelist?cidr=10.0.0.0%2F8"), HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(businessToken)), Void.class);
        assertThat(removeWhitelist.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        String timestamp2 = String.valueOf(System.currentTimeMillis() / 1000);
        String signature2 = hmacSha256Hex(secretKey, timestamp2 + "." + body);
        ResponseEntity<Map> allowed = signedCollect(publicKey, timestamp2, signature2, body);
        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void withdrawViaApiKeyWorksEndToEndGivenASettlementAccount() {
        UUID bankId = insertBank();
        Map<String, Object> bankAccountRequest = Map.of(
                "bankId", bankId.toString(), "accountNumber", "1234567890", "accountName", "Lamarr Settlement");
        ResponseEntity<Map> bankAccountResponse = restTemplate.exchange(
                url("/api/v1/bank-accounts"), HttpMethod.POST,
                new HttpEntity<>(bankAccountRequest, authHeaders(businessToken)), Map.class);
        String bankAccountId = (String) bankAccountResponse.getBody().get("id");
        restTemplate.exchange(url("/api/v1/settlement-accounts"), HttpMethod.POST,
                new HttpEntity<>(Map.of("bankAccountId", bankAccountId, "splitPercentage", new BigDecimal("100.00")),
                        authHeaders(businessToken)),
                Map.class);

        String collectBody = "{\"amount\":7000}";
        String collectTimestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String collectSignature = hmacSha256Hex(secretKey, collectTimestamp + "." + collectBody);
        ResponseEntity<Map> collect = signedCollect(publicKey, collectTimestamp, collectSignature, collectBody);
        assertThat(collect.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String withdrawBody = "{}";
        String withdrawTimestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String withdrawSignature = hmacSha256Hex(secretKey, withdrawTimestamp + "." + withdrawBody);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Public-Key", publicKey);
        headers.set("X-Timestamp", withdrawTimestamp);
        headers.set("X-Signature", withdrawSignature);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<List> withdraw = restTemplate.exchange(
                url("/api/v1/withdraw"), HttpMethod.POST, new HttpEntity<>(withdrawBody, headers), List.class);

        assertThat(withdraw.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withdraw.getBody()).hasSize(1);
        Map<String, Object> settlement = (Map<String, Object>) withdraw.getBody().get(0);
        assertThat(settlement.get("settlementStatus")).isEqualTo("COMPLETED");
        assertThat(((Number) settlement.get("amount")).longValue()).isEqualTo(7000L);
    }

    private ResponseEntity<Map> signedCollect(String publicKey, String timestamp, String signature, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Public-Key", publicKey);
        headers.set("X-Timestamp", timestamp);
        headers.set("X-Signature", signature);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url("/api/v1/collect"), HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
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
