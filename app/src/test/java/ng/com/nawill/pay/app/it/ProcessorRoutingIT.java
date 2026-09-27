package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ng.com.nawill.pay.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * FR-Proc-3/4: collections route to the highest-priority processor available
 * to the business that offers the requested method. The business is limited
 * to this test's own processors so other test classes' processors don't
 * interfere.
 */
class ProcessorRoutingIT extends AbstractIntegrationTest {

    private String token;
    private String businessId;
    private String virtualAccountId;
    private String cardAndTransfer;
    private String transferOnly;

    @BeforeEach
    void setUp() {
        Map<String, Object> signup = businessSignup("Route", "Owner", "SecurePass123!");
        token = (String) signup.get("accessToken");
        businessId = (String) signup.get("businessId");
        virtualAccountId = soleVirtualAccountId(token);
        cardAndTransfer = createPaymentProcessor(10, "TRANSFER", "CARD");
        transferOnly = createPaymentProcessor(5, "TRANSFER");
        limitBusinessToProcessors(businessId, Set.of(cardAndTransfer, transferOnly));
    }

    @Test
    void routesByPriorityThenByMethodAndRecordsTheMethod() {
        ResponseEntity<Map> transfer = collect(null, null);
        assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(transfer.getBody().get("paymentProcessorId")).isEqualTo(transferOnly);
        assertThat(transfer.getBody().get("paymentMethod")).isEqualTo("TRANSFER");

        ResponseEntity<Map> card = collect("CARD", null);
        assertThat(card.getBody().get("paymentProcessorId")).isEqualTo(cardAndTransfer);
        assertThat(card.getBody().get("paymentMethod")).isEqualTo("CARD");

        ResponseEntity<Map> ussd = collect("USSD", null);
        assertThat(ussd.getStatusCode()).isEqualTo(ErrorCode.PAYMENT_METHOD_UNAVAILABLE.status());
        assertThat(ussd.getBody().get("errorCode")).isEqualTo(ErrorCode.PAYMENT_METHOD_UNAVAILABLE.name());
    }

    @Test
    void aBusinessSettingOrThePlatformSwitchTakesAProcessorOutOfRouting() {
        adminExchange(HttpMethod.PUT, "/api/v1/admin/businesses/" + businessId + "/payment-processors/" + transferOnly,
                Map.of("enabled", false));
        assertThat(collect(null, null).getBody().get("paymentProcessorId")).isEqualTo(cardAndTransfer);

        adminExchange(HttpMethod.POST, "/api/v1/admin/payment-processors/" + cardAndTransfer + "/deactivate",
                superAdminPasswordConfirmation());
        ResponseEntity<Map> none = collect(null, null);
        assertThat(none.getBody().get("errorCode")).isEqualTo(ErrorCode.PAYMENT_METHOD_UNAVAILABLE.name());
    }

    @Test
    void aProcessorChosenByTheCallerMustBeAvailableAndOfferTheMethod() {
        ResponseEntity<Map> chosen = collect(null, cardAndTransfer);
        assertThat(chosen.getBody().get("paymentProcessorId")).isEqualTo(cardAndTransfer);

        ResponseEntity<Map> noCard = collect("CARD", transferOnly);
        assertThat(noCard.getBody().get("errorCode")).isEqualTo(ErrorCode.PAYMENT_PROCESSOR_UNAVAILABLE.name());

        adminExchange(HttpMethod.PUT, "/api/v1/admin/businesses/" + businessId + "/payment-processors/" + transferOnly,
                Map.of("enabled", false));
        ResponseEntity<Map> switchedOff = collect(null, transferOnly);
        assertThat(switchedOff.getBody().get("errorCode")).isEqualTo(ErrorCode.PAYMENT_PROCESSOR_UNAVAILABLE.name());
    }

    @Test
    @SuppressWarnings("unchecked")
    void availableMethodsAreListedForTheBusinessAndOnItsPaymentLinks() {
        ResponseEntity<List> methods = restTemplate.exchange(url("/api/v1/payment-methods"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), List.class);
        assertThat(((List<Map<String, Object>>) methods.getBody()).stream().map(m -> m.get("method")))
                .containsExactly("TRANSFER", "CARD");

        ResponseEntity<Map> link = restTemplate.exchange(url("/api/v1/payment-links"), HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", 1500, "linkType", "PERMANENT", "singleUse", false), idempotentHeaders(token)),
                Map.class);
        ResponseEntity<Map> checkout = restTemplate.exchange(url("/api/v1/pay/" + link.getBody().get("shortCode")),
                HttpMethod.GET, HttpEntity.EMPTY, Map.class);
        assertThat(checkout.getBody().get("shortCode")).isEqualTo(link.getBody().get("shortCode"));
        assertThat(((List<Map<String, Object>>) checkout.getBody().get("availableMethods")).stream().map(m -> m.get("label")))
                .containsExactly("Bank transfer", "Card");
    }

    private ResponseEntity<Map> collect(String method, String processorId) {
        Map<String, Object> body = new HashMap<>(Map.of("virtualAccountId", virtualAccountId,
                "transactionType", "CREDIT", "amount", 2500));
        if (method != null) {
            body.put("paymentMethod", method);
        }
        if (processorId != null) {
            body.put("paymentProcessorId", processorId);
        }
        return restTemplate.exchange(url("/api/v1/transactions"), HttpMethod.POST,
                new HttpEntity<>(body, idempotentHeaders(token)), Map.class);
    }
}
