package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FR-Auth-1 (peer-to-peer transfer) and FR-Auth-2 (transaction PIN gating
 * it) end to end - wallet-to-wallet ledger movement on top of the existing
 * ACID-safe balance infrastructure, no external processor involved.
 */
class TransferIT extends AbstractIntegrationTest {

    @Test
    void transferMovesBalanceBetweenAccountsAndAppearsInBothHistoriesLinked() {
        Wallet sender = newFundedWallet("Ada", "Sender", 1_000_000);
        Wallet recipient = newWallet("Bola", "Recipient");
        setPin(sender.token, "1234");

        ResponseEntity<Map> resolve = restTemplate.exchange(
                url("/api/v1/transfers/resolve?identifier=" + recipient.accountNumber), HttpMethod.GET,
                new HttpEntity<>(authHeaders(sender.token)), Map.class);
        assertThat(resolve.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) resolve.getBody().get("accountNumberMasked")).endsWith(recipient.accountNumber.substring(6));

        var headers = authHeaders(sender.token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<Map> transfer = restTemplate.exchange(url("/api/v1/transfers"), HttpMethod.POST,
                new HttpEntity<>(Map.of("recipientIdentifier", recipient.accountNumber, "amount", 250_000,
                        "narration", "lunch money", "transactionPin", "1234"), headers), Map.class);
        assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transfer.getBody().get("senderNewBalance")).isEqualTo(750_000);
        String transferGroupId = (String) transfer.getBody().get("transferGroupId");
        assertThat(transferGroupId).isNotNull();

        assertThat(balanceOf(sender.token)).isEqualTo(750_000);
        assertThat(balanceOf(recipient.token)).isEqualTo(250_000);

        List<Map<String, Object>> senderHistory = getPagedContent("/api/v1/transactions", authHeaders(sender.token));
        Map<String, Object> debitRow = senderHistory.stream()
                .filter(t -> transferGroupId.equals(t.get("transferGroupId"))).findFirst().orElseThrow();
        assertThat(debitRow.get("transactionType")).isEqualTo("DEBIT");
        assertThat(debitRow.get("paymentProcessorId")).isNull();
        assertThat(debitRow.get("counterpartyAccountId")).isEqualTo(recipient.virtualAccountId);

        List<Map<String, Object>> recipientHistory = getPagedContent("/api/v1/transactions", authHeaders(recipient.token));
        Map<String, Object> creditRow = recipientHistory.stream()
                .filter(t -> transferGroupId.equals(t.get("transferGroupId"))).findFirst().orElseThrow();
        assertThat(creditRow.get("transactionType")).isEqualTo("CREDIT");
        assertThat(creditRow.get("counterpartyAccountId")).isEqualTo(sender.virtualAccountId);
    }

    @Test
    void transferByPhoneNumberResolvesTheSameRecipient() {
        Wallet sender = newFundedWallet("Chidi", "Sender", 500_000);
        Wallet recipient = newWallet("Efe", "Recipient");
        setPin(sender.token, "1234");

        var headers = authHeaders(sender.token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<Map> transfer = restTemplate.exchange(url("/api/v1/transfers"), HttpMethod.POST,
                new HttpEntity<>(Map.of("recipientIdentifier", recipient.phoneNo, "amount", 100_000,
                        "transactionPin", "1234"), headers), Map.class);

        assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(balanceOf(recipient.token)).isEqualTo(100_000);
    }

    @Test
    void transferWithoutPinSetIsRejected() {
        Wallet sender = newFundedWallet("Grace", "Sender", 500_000);
        Wallet recipient = newWallet("Hope", "Recipient");

        var headers = authHeaders(sender.token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<Map> transfer = restTemplate.exchange(url("/api/v1/transfers"), HttpMethod.POST,
                new HttpEntity<>(Map.of("recipientIdentifier", recipient.accountNumber, "amount", 1_000,
                        "transactionPin", "1234"), headers), Map.class);

        assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(transfer.getBody().get("errorCode")).isEqualTo("PIN_NOT_SET");
    }

    @Test
    void wrongPinIsRejectedAndLocksTheTransferCapabilityAfterMaxAttempts() {
        Wallet sender = newFundedWallet("Ifeoma", "Sender", 500_000);
        Wallet recipient = newWallet("James", "Recipient");
        setPin(sender.token, "1234");

        ResponseEntity<Map> first = transferAttempt(sender.token, recipient.accountNumber, "0000");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat((String) first.getBody().get("message")).contains("1/3");

        ResponseEntity<Map> second = transferAttempt(sender.token, recipient.accountNumber, "0000");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat((String) second.getBody().get("message")).contains("2/3");

        ResponseEntity<Map> third = transferAttempt(sender.token, recipient.accountNumber, "0000");
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.LOCKED);

        // Locked out even with the CORRECT pin now.
        ResponseEntity<Map> fourth = transferAttempt(sender.token, recipient.accountNumber, "1234");
        assertThat(fourth.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
    }

    @Test
    void selfTransferIsRejected() {
        Wallet sender = newFundedWallet("Kemi", "Sender", 500_000);
        setPin(sender.token, "1234");

        ResponseEntity<Map> transfer = transferAttempt(sender.token, sender.accountNumber, "1234");

        assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(transfer.getBody().get("errorCode")).isEqualTo("SELF_TRANSFER");
    }

    @Test
    void transferExceedingBalanceIsRejected() {
        Wallet sender = newFundedWallet("Lola", "Sender", 1_000);
        Wallet recipient = newWallet("Musa", "Recipient");
        setPin(sender.token, "1234");

        ResponseEntity<Map> transfer = transferAttempt(sender.token, recipient.accountNumber, "1234", 999_999);

        assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(transfer.getBody().get("errorCode")).isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    void changingAnExistingPinRequiresTheCurrentPin() {
        Wallet sender = newFundedWallet("Nkem", "Sender", 500_000);
        setPin(sender.token, "1234");

        ResponseEntity<Map> wrongCurrentPin = restTemplate.exchange(url("/api/v1/auth/transaction-pin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", "SecurePass123!", "currentPin", "0000",
                        "pin", "5678", "confirmPin", "5678"), authHeaders(sender.token)), Map.class);
        assertThat(wrongCurrentPin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongCurrentPin.getBody().get("errorCode")).isEqualTo("INVALID_PIN");

        ResponseEntity<Map> correctChange = restTemplate.exchange(url("/api/v1/auth/transaction-pin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", "SecurePass123!", "currentPin", "1234",
                        "pin", "5678", "confirmPin", "5678"), authHeaders(sender.token)), Map.class);
        assertThat(correctChange.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---- helpers ----

    private ResponseEntity<Map> transferAttempt(String senderToken, String recipientAccountNumber, String pin) {
        return transferAttempt(senderToken, recipientAccountNumber, pin, 1_000);
    }

    private ResponseEntity<Map> transferAttempt(String senderToken, String recipientAccountNumber, String pin, int amount) {
        var headers = authHeaders(senderToken);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        return restTemplate.exchange(url("/api/v1/transfers"), HttpMethod.POST,
                new HttpEntity<>(Map.of("recipientIdentifier", recipientAccountNumber, "amount", amount,
                        "transactionPin", pin), headers), Map.class);
    }

    private void setPin(String token, String pin) {
        ResponseEntity<Map> response = restTemplate.exchange(url("/api/v1/auth/transaction-pin"), HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", "SecurePass123!", "pin", pin, "confirmPin", pin),
                        authHeaders(token)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private int balanceOf(String token) {
        List<Map<String, Object>> accounts = getPagedContent("/api/v1/virtual-accounts", authHeaders(token));
        return (Integer) accounts.get(0).get("balance");
    }

    private Wallet newWallet(String firstName, String lastName) {
        Map<String, Object> signup = uniqueSignupPayload(firstName, lastName, "SecurePass123!");
        String token = (String) restTemplate.postForEntity(url("/api/v1/auth/signup"), signup, Map.class).getBody().get("accessToken");
        List<Map<String, Object>> accounts = getPagedContent("/api/v1/virtual-accounts", authHeaders(token));
        String virtualAccountId = (String) accounts.get(0).get("id");
        String accountNumber = (String) accounts.get(0).get("accountNumber");
        return new Wallet(token, virtualAccountId, accountNumber, (String) signup.get("phoneNo"));
    }

    private Wallet newFundedWallet(String firstName, String lastName, int amount) {
        Wallet wallet = newWallet(firstName, lastName);
        String adminToken = superAdminToken();
        Map<String, Object> processorRequest = Map.of("name", "Processor-" + UUID.randomUUID());
        ResponseEntity<Map> processorResponse = restTemplate.exchange(url("/api/v1/payment-processors"), HttpMethod.POST,
                new HttpEntity<>(processorRequest, authHeaders(adminToken)), Map.class);
        String processorId = (String) processorResponse.getBody().get("id");

        var headers = authHeaders(wallet.token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<Map> fund = restTemplate.exchange(url("/api/v1/transactions"), HttpMethod.POST,
                new HttpEntity<>(Map.of("virtualAccountId", wallet.virtualAccountId, "paymentProcessorId", processorId,
                        "transactionType", "CREDIT", "amount", amount), headers), Map.class);
        assertThat(fund.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return wallet;
    }

    private record Wallet(String token, String virtualAccountId, String accountNumber, String phoneNo) {
    }
}
