package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Settlement bank accounts registered by a platform admin on a business's
 * behalf, plus the duplicate and malformed-input rules shared with the
 * business-scoped {@code /api/v1/bank-accounts}.
 */
class AdminBankAccountIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "SecurePass123!";

    @Test
    void superAdminRegistersBankAccountForABusinessWhichTheBusinessThenSees() {
        Map<String, Object> signup = businessSignup("Kemi", "Adeyemi", PASSWORD);
        String businessToken = (String) signup.get("accessToken");
        String businessId = (String) signup.get("businessId");
        UUID bankId = insertBank();

        ResponseEntity<Map> created = restTemplate.exchange(
                url("/api/v1/admin/businesses/" + businessId + "/bank-accounts"), HttpMethod.POST,
                new HttpEntity<>(bankAccountRequest(bankId, "0123456789"), authHeaders(superAdminToken())), Map.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("accountName")).isEqualTo("TEST ACCOUNT HOLDER");
        List<Map<String, Object>> businessView = getPagedContent("/api/v1/bank-accounts", authHeaders(businessToken));
        assertThat(businessView).extracting(account -> account.get("id")).containsExactly(created.getBody().get("id"));
        List<Map<String, Object>> adminView = getPagedContent(
                "/api/v1/admin/businesses/" + businessId + "/bank-accounts", authHeaders(superAdminToken()));
        assertThat(adminView).extracting(account -> account.get("id")).containsExactly(created.getBody().get("id"));
    }

    @Test
    void businessOwnerCannotRegisterBankAccountForAnotherBusiness() {
        String otherBusinessId = (String) businessSignup("Other", "Owner", PASSWORD).get("businessId");
        String token = businessSignupAndGetToken("Nosy", "Owner", PASSWORD);

        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/admin/businesses/" + otherBusinessId + "/bank-accounts"), HttpMethod.POST,
                new HttpEntity<>(bankAccountRequest(insertBank(), "0123456789"), authHeaders(token)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unknownBusinessIsNotFound() {
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/v1/admin/businesses/" + UUID.randomUUID() + "/bank-accounts"), HttpMethod.POST,
                new HttpEntity<>(bankAccountRequest(insertBank(), "0123456789"), authHeaders(superAdminToken())), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void sameAccountCannotBeRegisteredTwiceForOneBusinessByEitherCaller() {
        Map<String, Object> signup = businessSignup("Dupe", "Owner", PASSWORD);
        String businessToken = (String) signup.get("accessToken");
        Map<String, Object> request = bankAccountRequest(insertBank(), "0123456789");

        ResponseEntity<Map> first = restTemplate.exchange(url("/api/v1/bank-accounts"), HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(businessToken)), Map.class);
        ResponseEntity<Map> again = restTemplate.exchange(url("/api/v1/bank-accounts"), HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(businessToken)), Map.class);
        ResponseEntity<Map> viaAdmin = restTemplate.exchange(
                url("/api/v1/admin/businesses/" + signup.get("businessId") + "/bank-accounts"), HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(superAdminToken())), Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(viaAdmin.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void malformedBankIdIsABadRequestNotAServerError() {
        String token = businessSignupAndGetToken("Typo", "Owner", PASSWORD);
        Map<String, Object> request = Map.of("bankId", "not-a-uuid", "accountNumber", "0123456789");

        ResponseEntity<Map> body = restTemplate.exchange(url("/api/v1/bank-accounts"), HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(token)), Map.class);
        ResponseEntity<Map> query = restTemplate.exchange(
                url("/api/v1/banks/resolve-account?bankId=not-a-uuid&accountNumber=0123456789"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);

        assertThat(body.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body.getBody().get("errorCode")).isEqualTo(ErrorCode.VALIDATION_ERROR.name());
        assertThat(query.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(query.getBody().get("errorCode")).isEqualTo(ErrorCode.VALIDATION_ERROR.name());
    }

    private static Map<String, Object> bankAccountRequest(UUID bankId, String accountNumber) {
        return Map.of("bankId", bankId.toString(), "accountNumber", accountNumber);
    }

    private UUID insertBank() {
        UUID bankId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO banks (id, name, code) VALUES (?, ?, ?)",
                bankId, "Test Bank " + bankId, bankId.toString().substring(0, 8));
        return bankId;
    }
}
