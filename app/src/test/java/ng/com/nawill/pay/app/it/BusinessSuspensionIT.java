package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import ng.com.nawill.pay.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** FR-Admin-6: a deactivated business can't receive or move money, but its users keep read-only access. */
class BusinessSuspensionIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "SecurePass123!";

    private String token;
    private String email;
    private String businessId;
    private String virtualAccountId;

    @BeforeEach
    void setUp() {
        createPaymentProcessor();
        Map<String, Object> payload = uniqueBusinessSignupPayload("Suspended", "Owner", PASSWORD);
        email = (String) payload.get("email");
        Map<String, Object> signup = restTemplate.postForEntity(url("/api/v1/auth/signup"), payload, Map.class).getBody();
        token = (String) signup.get("accessToken");
        businessId = (String) signup.get("businessId");
        virtualAccountId = soleVirtualAccountId(token);
    }

    @Test
    void deactivatingStopsMoneyMovementButKeepsReadOnlyAccess() {
        String shortCode = (String) restTemplate.exchange(url("/api/v1/payment-links"), HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", 1000, "linkType", "PERMANENT", "singleUse", false), idempotentHeaders(token)),
                Map.class).getBody().get("shortCode");

        ResponseEntity<Map> deactivated = adminExchange(HttpMethod.POST, "/api/v1/admin/businesses/" + businessId + "/deactivate",
                Map.of("reason", "Chargeback investigation"));
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) deactivated.getBody().get("summary")).get("status")).isEqualTo("INACTIVE");
        assertThat(deactivated.getBody().get("statusReason")).isEqualTo("Chargeback investigation");

        assertInactive(restTemplate.exchange(url("/api/v1/transactions"), HttpMethod.POST, new HttpEntity<>(
                Map.of("virtualAccountId", virtualAccountId, "transactionType", "CREDIT", "amount", 1000),
                idempotentHeaders(token)), Map.class));
        assertInactive(restTemplate.exchange(url("/api/v1/pay/" + shortCode), HttpMethod.POST,
                new HttpEntity<>(Map.of(), idempotentHeaders(token)), Map.class));
        assertInactive(restTemplate.exchange(url("/api/v1/settlements"), HttpMethod.POST,
                new HttpEntity<>(Map.of(), idempotentHeaders(token)), Map.class));
        String recipientAccount = (String) getPagedContent("/api/v1/virtual-accounts",
                authHeaders(signupAndGetToken("Recipient", "Person", PASSWORD))).get(0).get("accountNumber");
        assertInactive(restTemplate.exchange(url("/api/v1/transfers"), HttpMethod.POST, new HttpEntity<>(
                Map.of("recipientIdentifier", recipientAccount, "amount", 100, "transactionPin", "1234"),
                idempotentHeaders(token)), Map.class));

        ResponseEntity<Map> checkout = restTemplate.exchange(url("/api/v1/pay/" + shortCode), HttpMethod.GET,
                HttpEntity.EMPTY, Map.class);
        assertThat((List<?>) checkout.getBody().get("availableMethods")).isEmpty();

        String freshToken = loginAndGetToken(email, PASSWORD);
        assertThat(freshToken).as("users can still sign in").isNotBlank();
        ResponseEntity<Map> me = restTemplate.exchange(url("/api/v1/users/me"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(freshToken)), Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("businessActive")).isEqualTo(false);
        assertThat(getPagedContent("/api/v1/transactions", authHeaders(freshToken))).isNotNull();

        List<Map<String, Object>> inactiveBusinesses = getPagedContent("/api/v1/admin/businesses?status=INACTIVE&size=100",
                authHeaders(superAdminToken()));
        assertThat(inactiveBusinesses).allMatch(b -> "INACTIVE".equals(b.get("status")))
                .anyMatch(b -> businessId.equals(b.get("id")));
    }

    @Test
    void reactivatingRestoresCollectionsAndBothChangesAreAudited() {
        adminExchange(HttpMethod.POST, "/api/v1/admin/businesses/" + businessId + "/deactivate", Map.of("reason", "Review"));
        ResponseEntity<Map> activated = adminExchange(HttpMethod.POST, "/api/v1/admin/businesses/" + businessId + "/activate", null);
        assertThat(((Map<?, ?>) activated.getBody().get("summary")).get("status")).isEqualTo("ACTIVE");
        assertThat(activated.getBody().get("statusReason")).isNull();

        ResponseEntity<Map> collected = restTemplate.exchange(url("/api/v1/transactions"), HttpMethod.POST, new HttpEntity<>(
                Map.of("virtualAccountId", virtualAccountId, "transactionType", "CREDIT", "amount", 1000),
                idempotentHeaders(token)), Map.class);
        assertThat(collected.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        for (String event : List.of("BUSINESS_DEACTIVATED", "BUSINESS_ACTIVATED")) {
            assertThat(getPagedContent("/api/v1/admin/audit-logs?eventType=" + event + "&businessId=" + businessId,
                    authHeaders(superAdminToken()))).as(event).isNotEmpty();
        }
    }

    @Test
    void deactivationNeedsAReasonAndThePlatformPermission() {
        ResponseEntity<Map> noReason = adminExchange(HttpMethod.POST, "/api/v1/admin/businesses/" + businessId + "/deactivate",
                Map.of("reason", " "));
        assertThat(noReason.getBody().get("errorCode")).isEqualTo(ErrorCode.VALIDATION_ERROR.name());

        ResponseEntity<Map> byTheBusiness = restTemplate.exchange(
                url("/api/v1/admin/businesses/" + businessId + "/deactivate"), HttpMethod.POST,
                new HttpEntity<>(Map.of("reason", "Self"), authHeaders(token)), Map.class);
        assertThat(byTheBusiness.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static void assertInactive(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.BUSINESS_INACTIVE.status());
        assertThat(response.getBody().get("errorCode")).isEqualTo(ErrorCode.BUSINESS_INACTIVE.name());
    }
}
