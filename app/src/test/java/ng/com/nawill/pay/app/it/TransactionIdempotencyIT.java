package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import ng.com.nawill.pay.payments.transaction.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FR-Txn-1 / doc 3 §1: idempotency-key enforcement, replay of the original
 * response on a duplicate key, and no duplicate row created.
 */
class TransactionIdempotencyIT extends AbstractIntegrationTest {

    @Autowired
    private TransactionRepository transactionRepository;

    private String userToken;
    private String virtualAccountId;
    private String paymentProcessorId;

    @BeforeEach
    void setUp() {
        userToken = signupAndGetToken("Katherine", "Johnson", "SecurePass123");

        ResponseEntity<List> accounts = restTemplate.exchange(
                url("/api/v1/virtual-accounts"), HttpMethod.GET, new HttpEntity<>(authHeaders(userToken)), List.class);
        virtualAccountId = (String) ((Map<String, Object>) accounts.getBody().get(0)).get("id");

        String adminToken = superAdminToken();
        Map<String, Object> processorRequest = Map.of("name", "Paystack-" + UUID.randomUUID());
        ResponseEntity<Map> processorResponse = restTemplate.exchange(
                url("/api/v1/payment-processors"), HttpMethod.POST,
                new HttpEntity<>(processorRequest, authHeaders(adminToken)), Map.class);
        paymentProcessorId = (String) processorResponse.getBody().get("id");
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/transactions"), HttpMethod.POST,
                new HttpEntity<>(transactionRequest(), authHeaders(userToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void duplicateIdempotencyKeyReplaysOriginalResponseWithoutDuplicateRow() {
        String idempotencyKey = UUID.randomUUID().toString();
        var headers = authHeaders(userToken);
        headers.set("Idempotency-Key", idempotencyKey);

        ResponseEntity<Map> first = restTemplate.exchange(
                url("/api/v1/transactions"), HttpMethod.POST, new HttpEntity<>(transactionRequest(), headers), Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().get("transactionStatus")).isEqualTo("PAID");

        ResponseEntity<Map> replay = restTemplate.exchange(
                url("/api/v1/transactions"), HttpMethod.POST, new HttpEntity<>(transactionRequest(), headers), Map.class);

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getBody()).isEqualTo(first.getBody());

        long matchingRows = transactionRepository.findAll().stream()
                .filter(tx -> idempotencyKey.equals(tx.getIdempotencyKey()))
                .count();
        assertThat(matchingRows).isEqualTo(1);
    }

    private Map<String, Object> transactionRequest() {
        return Map.of(
                "virtualAccountId", virtualAccountId,
                "paymentProcessorId", paymentProcessorId,
                "transactionType", "CREDIT",
                "amount", 5000
        );
    }
}
