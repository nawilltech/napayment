package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FR-Txn: the paginated/filterable transaction list and the analytics
 * summary over the same filter set (doc 2 §4.2). Row-level ownership scoping
 * (a caller only ever sees their own virtual account's transactions) is the
 * behaviour most worth locking down here since {@link
 * ng.com.nawill.pay.payments.transaction.TransactionSpecifications} is new.
 */
class TransactionListAndAnalyticsIT extends AbstractIntegrationTest {

    private String userToken;
    private String virtualAccountId;
    private String paymentProcessorId;

    @BeforeEach
    void setUp() {
        userToken = signupAndGetToken("Ada", "Lovelace", "SecurePass123!");

        List<Map<String, Object>> accounts = getPagedContent("/api/v1/virtual-accounts", authHeaders(userToken));
        virtualAccountId = (String) accounts.get(0).get("id");

        String adminToken = superAdminToken();
        Map<String, Object> processorRequest = Map.of("name", "Paystack-" + UUID.randomUUID());
        ResponseEntity<Map> processorResponse = restTemplate.exchange(
                url("/api/v1/payment-processors"), HttpMethod.POST,
                new HttpEntity<>(processorRequest, authHeaders(adminToken)), Map.class);
        paymentProcessorId = (String) processorResponse.getBody().get("id");
    }

    @Test
    void listReturnsOnlyCallersOwnTransactionsAndSupportsFilters() {
        Map<String, Object> created = createTransaction(5000);
        String sessionId = (String) created.get("sessionId");

        List<Map<String, Object>> unfiltered = getPagedContent("/api/v1/transactions", authHeaders(userToken));
        assertThat(unfiltered).extracting(tx -> tx.get("id")).contains(created.get("id"));

        List<Map<String, Object>> termFiltered = getPagedContent(
                "/api/v1/transactions?term=" + sessionId.substring(0, 8), authHeaders(userToken));
        assertThat(termFiltered).extracting(tx -> tx.get("id")).contains(created.get("id"));

        List<Map<String, Object>> statusFiltered = getPagedContent(
                "/api/v1/transactions?status=FAILED", authHeaders(userToken));
        assertThat(statusFiltered).extracting(tx -> tx.get("id")).doesNotContain(created.get("id"));

        String otherUserToken = signupAndGetToken("Grace", "Hopper", "SecurePass123!");
        List<Map<String, Object>> otherUsersView = getPagedContent("/api/v1/transactions", authHeaders(otherUserToken));
        assertThat(otherUsersView).extracting(tx -> tx.get("id")).doesNotContain(created.get("id"));
    }

    @Test
    void listRejectsFromDateAfterToDate() {
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/transactions?fromDate=2026-06-01T00:00:00Z&toDate=2026-01-01T00:00:00Z"),
                HttpMethod.GET, new HttpEntity<>(authHeaders(userToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void analyticsSummarisesOnlyCallersOwnMatchingTransactions() {
        createTransaction(3000);
        createTransaction(7000);

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/transactions/analytics"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(userToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(((Number) body.get("totalCount")).longValue()).isEqualTo(2);
        assertThat(new BigInteger(body.get("totalVolume").toString())).isEqualTo(BigInteger.valueOf(10000));
        @SuppressWarnings("unchecked")
        Map<String, Object> highest = (Map<String, Object>) body.get("highest");
        assertThat(new BigInteger(highest.get("amount").toString())).isEqualTo(BigInteger.valueOf(7000));
        assertThat((List<?>) body.get("byStatus")).isNotEmpty();
        assertThat((List<?>) body.get("byType")).isNotEmpty();
        assertThat((List<?>) body.get("dailyVolume")).isNotEmpty();
    }

    @Test
    void analyticsWithNoMatchingTransactionsReturnsZeroedSummary() {
        String freshUserToken = signupAndGetToken("Margaret", "Hamilton", "SecurePass123!");

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/transactions/analytics"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(freshUserToken)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(((Number) body.get("totalCount")).longValue()).isEqualTo(0);
        assertThat(body.get("highest")).isNull();
        assertThat(body.get("lowest")).isNull();
    }

    private Map<String, Object> createTransaction(int amount) {
        var headers = authHeaders(userToken);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        Map<String, Object> request = Map.of(
                "virtualAccountId", virtualAccountId,
                "paymentProcessorId", paymentProcessorId,
                "transactionType", "CREDIT",
                "amount", amount
        );
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/transactions"), HttpMethod.POST, new HttpEntity<>(request, headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
}
