package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** FR-DynAcct-1: temporary/expiring virtual accounts for bank-transfer reconciliation. */
class DynamicVirtualAccountIT extends AbstractIntegrationTest {

    @Test
    void mintsAndDepositsThenRejectsASecondDeposit() {
        String token = businessSignupAndGetToken("Grace", "Hopper", "SecurePass123!");

        ResponseEntity<Map> minted = restTemplate.exchange(url("/api/v1/temporary-accounts"), HttpMethod.POST,
                new HttpEntity<>(Map.of("expectedAmount", 3500, "reference", "invoice-1"), authHeaders(token)), Map.class);
        assertThat(minted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String accountNumber = (String) minted.getBody().get("accountNumber");
        assertThat(accountNumber).hasSize(10);
        assertThat(accountNumber).startsWith("9");

        var depositHeaders = authHeaders(token);
        depositHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<Map> deposit = restTemplate.exchange(
                url("/api/v1/temporary-accounts/" + accountNumber + "/simulate-deposit"), HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", 3500), depositHeaders), Map.class);
        assertThat(deposit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deposit.getBody().get("transactionStatus")).isEqualTo("PAID");

        var secondDepositHeaders = authHeaders(token);
        secondDepositHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<Map> secondDeposit = restTemplate.exchange(
                url("/api/v1/temporary-accounts/" + accountNumber + "/simulate-deposit"), HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", 3500), secondDepositHeaders), Map.class);
        assertThat(secondDeposit.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(secondDeposit.getBody().get("errorCode")).isEqualTo("DYNAMIC_ACCOUNT_NOT_DEPOSITABLE");
    }

    @Test
    void expiredAccountRejectsDeposit() {
        String token = businessSignupAndGetToken("Katherine", "Johnson", "SecurePass123!");

        Map<String, Object> mintRequest = Map.of(
                "expectedAmount", 1000, "expiresAt", Instant.now().minusSeconds(60).toString(), "reference", "invoice-2");
        ResponseEntity<Map> minted = restTemplate.exchange(
                url("/api/v1/temporary-accounts"), HttpMethod.POST, new HttpEntity<>(mintRequest, authHeaders(token)), Map.class);
        assertThat(minted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String accountNumber = (String) minted.getBody().get("accountNumber");

        var depositHeaders = authHeaders(token);
        depositHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<Map> deposit = restTemplate.exchange(
                url("/api/v1/temporary-accounts/" + accountNumber + "/simulate-deposit"), HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", 1000), depositHeaders), Map.class);

        assertThat(deposit.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(deposit.getBody().get("errorCode")).isEqualTo("DYNAMIC_ACCOUNT_NOT_DEPOSITABLE");
    }

    @Test
    void anotherBusinessCannotDepositToSomeoneElsesDynamicAccount() {
        String ownerToken = businessSignupAndGetToken("Radia", "Perlman", "SecurePass123!");
        String otherToken = businessSignupAndGetToken("Barbara", "Liskov", "SecurePass123!");

        ResponseEntity<Map> minted = restTemplate.exchange(url("/api/v1/temporary-accounts"), HttpMethod.POST,
                new HttpEntity<>(Map.of("expectedAmount", 2000), authHeaders(ownerToken)), Map.class);
        String accountNumber = (String) minted.getBody().get("accountNumber");

        var depositHeaders = authHeaders(otherToken);
        depositHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<Map> deposit = restTemplate.exchange(
                url("/api/v1/temporary-accounts/" + accountNumber + "/simulate-deposit"), HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", 2000), depositHeaders), Map.class);

        assertThat(deposit.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
