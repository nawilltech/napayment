package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** FR-Proc-2: the payment method catalogue - seeded defaults and CRUD. */
class PaymentMethodCatalogIT extends AbstractIntegrationTest {

    private static final String METHODS = "/api/v1/admin/payment-methods";

    @Test
    @SuppressWarnings("unchecked")
    void theDefaultMethodsAreSeededInDisplayOrder() {
        ResponseEntity<List> list = restTemplate.exchange(url(METHODS), HttpMethod.GET,
                new HttpEntity<>(authHeaders(superAdminToken())), List.class);
        List<Map<String, Object>> rows = list.getBody();
        assertThat(rows).extracting(row -> row.get("code"))
                .containsSubsequence("TRANSFER", "CARD", "USSD", "BANK_DEBIT", "QR");
        assertThat(rows).filteredOn(row -> "TRANSFER".equals(row.get("code")))
                .singleElement().satisfies(row -> assertThat(row.get("name")).isEqualTo("Bank transfer"));
    }

    @Test
    void createsEditsAndDeletesAnUnusedMethodAndRejectsDuplicates() {
        String code = "M_" + unique();
        ResponseEntity<Map> created = adminExchange(HttpMethod.POST, METHODS,
                Map.of("code", code.toLowerCase(), "name", "Mobile money " + code, "description", "Wallet push", "displayOrder", 60));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("code")).isEqualTo(code);
        assertThat(created.getBody().get("status")).isEqualTo("ACTIVE");
        String id = (String) created.getBody().get("id");

        assertError(adminExchange(HttpMethod.POST, METHODS, Map.of("code", code, "name", "Another " + code)),
                ErrorCode.PAYMENT_METHOD_CODE_TAKEN);
        assertError(adminExchange(HttpMethod.POST, METHODS, Map.of("code", "X_" + unique(), "name", "mobile MONEY " + code)),
                ErrorCode.PAYMENT_METHOD_NAME_TAKEN);
        assertError(adminExchange(HttpMethod.POST, METHODS, Map.of("code", "1bad", "name", "Bad " + code)),
                ErrorCode.VALIDATION_ERROR);

        ResponseEntity<Map> updated = adminExchange(HttpMethod.PATCH, METHODS + "/" + id,
                Map.of("name", "Wallet " + code, "description", "", "displayOrder", 5));
        assertThat(updated.getBody().get("name")).isEqualTo("Wallet " + code);
        assertThat(updated.getBody().get("description")).isNull();
        assertThat(((Number) updated.getBody().get("displayOrder")).intValue()).isEqualTo(5);

        assertThat(adminExchange(HttpMethod.DELETE, METHODS + "/" + id, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertError(adminExchange(HttpMethod.GET, METHODS + "/" + id, null), ErrorCode.PAYMENT_METHOD_NOT_FOUND);
    }

    @Test
    void aMethodInUseCanOnlyBeDeactivatedAndDeactivationStopsItsPayments() {
        String code = "W_" + unique();
        String id = (String) adminExchange(HttpMethod.POST, METHODS, Map.of("code", code, "name", "Wallet " + code))
                .getBody().get("id");
        String processor = createPaymentProcessor(10, "TRANSFER", code);
        Map<String, Object> signup = businessSignup("Method", "User", "SecurePass123!");
        String token = (String) signup.get("accessToken");
        limitBusinessToProcessors((String) signup.get("businessId"), Set.of(processor));
        String account = soleVirtualAccountId(token);

        assertThat(collect(token, account, code).getBody().get("paymentMethod")).isEqualTo(code);
        assertThat(((Number) adminExchange(HttpMethod.GET, METHODS + "/" + id, null).getBody().get("processorCount")).intValue())
                .isEqualTo(1);
        assertError(adminExchange(HttpMethod.DELETE, METHODS + "/" + id, null), ErrorCode.PAYMENT_METHOD_IN_USE);

        assertError(adminExchange(HttpMethod.POST, METHODS + "/" + id + "/deactivate", Map.of("password", "wrong")),
                ErrorCode.PASSWORD_CONFIRMATION_FAILED);
        ResponseEntity<Map> deactivated = adminExchange(HttpMethod.POST, METHODS + "/" + id + "/deactivate",
                superAdminPasswordConfirmation());
        assertThat(deactivated.getBody().get("status")).isEqualTo("INACTIVE");
        assertError(collect(token, account, code), ErrorCode.PAYMENT_METHOD_UNAVAILABLE);

        adminExchange(HttpMethod.POST, METHODS + "/" + id + "/activate", superAdminPasswordConfirmation());
        assertThat(collect(token, account, code).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void processorsCanOnlyOfferCatalogueMethods() {
        String suffix = unique();
        assertError(adminExchange(HttpMethod.POST, "/api/v1/admin/payment-processors", Map.of("name", "Unknown " + suffix,
                "code", "U_" + suffix, "methods", List.of("TELEPATHY"))), ErrorCode.PAYMENT_METHOD_NOT_FOUND);
        String processor = createPaymentProcessor(100, "TRANSFER");
        assertError(adminExchange(HttpMethod.PUT, "/api/v1/admin/payment-processors/" + processor + "/methods/TELEPATHY", null),
                ErrorCode.PAYMENT_METHOD_NOT_FOUND);
        ResponseEntity<Map> withCard = adminExchange(HttpMethod.PUT,
                "/api/v1/admin/payment-processors/" + processor + "/methods/card", null);
        assertThat(withCard.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map> collect(String token, String virtualAccountId, String method) {
        return restTemplate.exchange(url("/api/v1/transactions"), HttpMethod.POST, new HttpEntity<>(
                Map.of("virtualAccountId", virtualAccountId, "transactionType", "CREDIT", "amount", 1000,
                        "paymentMethod", method), idempotentHeaders(token)), Map.class);
    }

    private static void assertError(ResponseEntity<Map> response, ErrorCode expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected.status());
        assertThat(response.getBody().get("errorCode")).isEqualTo(expected.name());
    }

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
