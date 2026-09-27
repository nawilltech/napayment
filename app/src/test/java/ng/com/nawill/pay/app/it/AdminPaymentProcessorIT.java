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

/** FR-Proc-1/2 and FR-Admin-5/7: the processor catalogue, its methods and switches, and per-business settings. */
class AdminPaymentProcessorIT extends AbstractIntegrationTest {

    private static final String PROCESSORS = "/api/v1/admin/payment-processors";

    @Test
    void createsAProcessorWithMethodsAndRejectsDuplicatesOrNoMethods() {
        String suffix = unique();
        Map<String, Object> body = Map.of("name", "Paystack " + suffix, "code", "ps_" + suffix, "priority", 5,
                "methods", List.of("CARD", "USSD", "TRANSFER"));

        ResponseEntity<Map> created = adminExchange(HttpMethod.POST, PROCESSORS, body);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("code")).isEqualTo(("PS_" + suffix).toUpperCase());
        assertThat(created.getBody().get("status")).isEqualTo("ACTIVE");
        assertThat(created.getBody().get("defaultEnabled")).isEqualTo(true);
        assertThat(methods(created)).containsExactlyInAnyOrder("CARD", "USSD", "TRANSFER");

        assertError(adminExchange(HttpMethod.POST, PROCESSORS, Map.of("name", "paystack " + suffix, "code", "OTHER_" + suffix,
                "methods", List.of("CARD"))), ErrorCode.PAYMENT_PROCESSOR_NAME_TAKEN);
        assertError(adminExchange(HttpMethod.POST, PROCESSORS, Map.of("name", "Other " + suffix, "code", "PS_" + suffix,
                "methods", List.of("CARD"))), ErrorCode.PAYMENT_PROCESSOR_CODE_TAKEN);
        assertError(adminExchange(HttpMethod.POST, PROCESSORS, Map.of("name", "Empty " + suffix, "code", "E_" + suffix,
                "methods", List.of())), ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void methodsCanBeDisabledKeptInHistoryAndReEnabled() {
        String id = createPaymentProcessor(100, "TRANSFER", "CARD");

        ResponseEntity<Map> disabled = adminExchange(HttpMethod.DELETE, PROCESSORS + "/" + id + "/methods/CARD", null);
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(methodActive(disabled, "CARD")).isFalse();

        assertError(adminExchange(HttpMethod.DELETE, PROCESSORS + "/" + id + "/methods/QR", null),
                ErrorCode.PAYMENT_METHOD_NOT_OFFERED);

        adminExchange(HttpMethod.PUT, PROCESSORS + "/" + id + "/methods/CARD", null);
        ResponseEntity<Map> withQr = adminExchange(HttpMethod.PUT, PROCESSORS + "/" + id + "/methods/QR", null);
        assertThat(methodActive(withQr, "CARD")).isTrue();
        assertThat(methodActive(withQr, "QR")).isTrue();
    }

    @Test
    void renamesAndReprioritisesButNotOntoAnotherProcessorsName() {
        String first = createPaymentProcessor(100, "TRANSFER");
        String second = createPaymentProcessor(100, "TRANSFER");
        String firstName = (String) adminExchange(HttpMethod.GET, PROCESSORS + "/" + first, null).getBody().get("name");

        ResponseEntity<Map> updated = adminExchange(HttpMethod.PATCH, PROCESSORS + "/" + second,
                Map.of("name", "Renamed " + unique(), "priority", 7));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) updated.getBody().get("priority")).intValue()).isEqualTo(7);

        assertError(adminExchange(HttpMethod.PATCH, PROCESSORS + "/" + second, Map.of("name", firstName)),
                ErrorCode.PAYMENT_PROCESSOR_NAME_TAKEN);
    }

    @Test
    void platformSwitchRequiresTheStaffPasswordAndCountsWrongAttempts() {
        String id = createPaymentProcessor(100, "TRANSFER");

        ResponseEntity<Map> wrong = adminExchange(HttpMethod.POST, PROCESSORS + "/" + id + "/deactivate",
                Map.of("password", "not-the-password"));
        assertError(wrong, ErrorCode.PASSWORD_CONFIRMATION_FAILED);
        assertThat((String) wrong.getBody().get("message")).contains("1/");
        assertThat(adminExchange(HttpMethod.GET, PROCESSORS + "/" + id, null).getBody().get("status")).isEqualTo("ACTIVE");

        ResponseEntity<Map> deactivated = adminExchange(HttpMethod.POST, PROCESSORS + "/" + id + "/deactivate",
                superAdminPasswordConfirmation());
        assertThat(deactivated.getBody().get("status")).isEqualTo("INACTIVE");
        ResponseEntity<Map> activated = adminExchange(HttpMethod.POST, PROCESSORS + "/" + id + "/activate",
                superAdminPasswordConfirmation());
        assertThat(activated.getBody().get("status")).isEqualTo("ACTIVE");

        // The successful confirmation reset the counter: the next mistake is attempt 1 again, not 2.
        ResponseEntity<Map> wrongAgain = adminExchange(HttpMethod.POST, PROCESSORS + "/" + id + "/deactivate",
                Map.of("password", "still-not-it"));
        assertThat((String) wrongAgain.getBody().get("message")).contains("1/");
        adminExchange(HttpMethod.POST, PROCESSORS + "/" + id + "/activate", superAdminPasswordConfirmation());
    }

    @Test
    void forAllBusinessesSetsTheDefaultAndClearsEveryBusinessSetting() {
        String id = createPaymentProcessor(100, "TRANSFER");
        String businessA = (String) businessSignup("Bulk", "Alpha", "SecurePass123!").get("businessId");
        String businessB = (String) businessSignup("Bulk", "Beta", "SecurePass123!").get("businessId");
        setForBusiness(businessA, id, false);
        setForBusiness(businessB, id, true);
        assertThat(processorFor(businessA, id).get("source")).isEqualTo("BUSINESS_SETTING");

        assertError(adminExchange(HttpMethod.POST, PROCESSORS + "/" + id + "/enable-for-all-businesses",
                Map.of("password", "wrong-password")), ErrorCode.PASSWORD_CONFIRMATION_FAILED);
        ResponseEntity<Map> enabled = adminExchange(HttpMethod.POST, PROCESSORS + "/" + id + "/enable-for-all-businesses",
                superAdminPasswordConfirmation());

        assertThat(enabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) enabled.getBody().get("clearedBusinessSettings")).intValue()).isEqualTo(2);
        Map<String, Object> afterEnable = processorFor(businessA, id);
        assertThat(afterEnable.get("source")).isEqualTo("PLATFORM_DEFAULT");
        assertThat(afterEnable.get("available")).isEqualTo(true);

        ResponseEntity<Map> disabled = adminExchange(HttpMethod.POST, PROCESSORS + "/" + id + "/disable-for-all-businesses",
                superAdminPasswordConfirmation());
        assertThat(((Number) disabled.getBody().get("clearedBusinessSettings")).intValue()).isZero();
        assertThat(((Map<?, ?>) disabled.getBody().get("processor")).get("defaultEnabled")).isEqualTo(false);
        assertThat(processorFor(businessB, id).get("available")).isEqualTo(false);

        List<Map<String, Object>> audit = getPagedContent(
                "/api/v1/admin/audit-logs?eventType=PAYMENT_PROCESSOR_ENABLED_FOR_ALL", authHeaders(superAdminToken()));
        assertThat(audit).anyMatch(row -> String.valueOf(row.get("detail")).contains(id)
                && String.valueOf(row.get("detail")).contains("clearedBusinessSettings=2"));
    }

    @Test
    void aBusinessCanBeSwitchedOnOffAndResetAndSeesWhyEachProcessorApplies() {
        String id = createPaymentProcessor(100, "TRANSFER", "USSD");
        String businessId = (String) businessSignup("Setting", "Owner", "SecurePass123!").get("businessId");

        Map<String, Object> byDefault = processorFor(businessId, id);
        assertThat(byDefault.get("businessSetting")).isNull();
        assertThat(byDefault.get("source")).isEqualTo("PLATFORM_DEFAULT");

        Map<String, Object> off = setForBusiness(businessId, id, false);
        assertThat(off.get("businessSetting")).isEqualTo(false);
        assertThat(off.get("available")).isEqualTo(false);

        ResponseEntity<Map> reset = adminExchange(HttpMethod.DELETE,
                "/api/v1/admin/businesses/" + businessId + "/payment-processors/" + id, null);
        assertThat(reset.getBody().get("businessSetting")).isNull();
        assertThat(reset.getBody().get("available")).isEqualTo(true);

        setForBusiness(businessId, id, true);
        adminExchange(HttpMethod.POST, PROCESSORS + "/" + id + "/deactivate", superAdminPasswordConfirmation());
        Map<String, Object> inactive = processorFor(businessId, id);
        assertThat(inactive.get("source")).isEqualTo("PROCESSOR_INACTIVE");
        assertThat(inactive.get("available")).isEqualTo(false);
        assertThat(inactive.get("businessSetting")).as("kept for reactivation").isEqualTo(true);
    }

    /** A real 1x1 PNG. */
    private static final String PNG_LOGO = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

    @Test
    void anOptionalLogoIsStoredAsABase64DataUrlAndCanBeReplacedOrRemoved() {
        String suffix = unique();
        ResponseEntity<Map> created = adminExchange(HttpMethod.POST, PROCESSORS, Map.of("name", "Logo " + suffix,
                "code", "LOGO_" + suffix, "methods", List.of("CARD"), "logo", PNG_LOGO));
        assertThat(created.getBody().get("logo")).isEqualTo(PNG_LOGO);
        String id = (String) created.getBody().get("id");
        assertThat(adminExchange(HttpMethod.GET, PROCESSORS + "/" + createPaymentProcessor(), null).getBody().get("logo"))
                .as("optional").isNull();

        String businessId = (String) businessSignup("Logo", "Viewer", "SecurePass123!").get("businessId");
        assertThat(processorFor(businessId, id).get("logo")).isEqualTo(PNG_LOGO);

        ResponseEntity<Map> removed = adminExchange(HttpMethod.DELETE, PROCESSORS + "/" + id + "/logo", null);
        assertThat(removed.getBody().get("logo")).isNull();
        ResponseEntity<Map> replaced = adminExchange(HttpMethod.PUT, PROCESSORS + "/" + id + "/logo", Map.of("logo", PNG_LOGO));
        assertThat(replaced.getBody().get("logo")).isEqualTo(PNG_LOGO);
    }

    @Test
    void logosMustBeSmallPngJpegOrWebpDataUrls() {
        String id = createPaymentProcessor();
        String svg = "data:image/svg+xml;base64,"
                + java.util.Base64.getEncoder().encodeToString("<svg><script>alert(1)</script></svg>".getBytes());
        String tooBig = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(new byte[101 * 1024]);
        for (String logo : List.of(svg, tooBig, "data:image/png;base64,not*base64", "https://example.com/logo.png")) {
            ResponseEntity<Map> rejected = adminExchange(HttpMethod.PUT, PROCESSORS + "/" + id + "/logo", Map.of("logo", logo));
            assertError(rejected, ErrorCode.INVALID_LOGO);
        }
        assertError(adminExchange(HttpMethod.POST, PROCESSORS, Map.of("name", "Bad logo " + unique(), "code", "BL_" + unique(),
                "methods", List.of("CARD"), "logo", svg)), ErrorCode.INVALID_LOGO);
    }

    @Test
    void businessAccountsCannotReachProcessorSetupAndUnknownBusinessesAre404() {
        String token = businessSignupAndGetToken("Nosy", "Merchant", "SecurePass123!");
        ResponseEntity<Map> list = restTemplate.exchange(url(PROCESSORS), HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertError(adminExchange(HttpMethod.PUT, "/api/v1/admin/businesses/" + UUID.randomUUID() + "/payment-processors/"
                + createPaymentProcessor(), Map.of("enabled", true)), ErrorCode.BUSINESS_NOT_FOUND);
    }

    private Map<String, Object> setForBusiness(String businessId, String processorId, boolean enabled) {
        ResponseEntity<Map> response = adminExchange(HttpMethod.PUT,
                "/api/v1/admin/businesses/" + businessId + "/payment-processors/" + processorId, Map.of("enabled", enabled));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> processorFor(String businessId, String processorId) {
        ResponseEntity<List> rows = restTemplate.exchange(url("/api/v1/admin/businesses/" + businessId + "/payment-processors"),
                HttpMethod.GET, new HttpEntity<>(authHeaders(superAdminToken())), List.class);
        return ((List<Map<String, Object>>) rows.getBody()).stream()
                .filter(row -> processorId.equals(row.get("processorId")))
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<String> methods(ResponseEntity<Map> processor) {
        return ((List<Map<String, Object>>) processor.getBody().get("methods")).stream()
                .map(m -> (String) m.get("method")).toList();
    }

    @SuppressWarnings("unchecked")
    private static boolean methodActive(ResponseEntity<Map> processor, String method) {
        return ((List<Map<String, Object>>) processor.getBody().get("methods")).stream()
                .filter(m -> method.equals(m.get("method")))
                .map(m -> (Boolean) m.get("active"))
                .findFirst().orElse(false);
    }

    private static void assertError(ResponseEntity<Map> response, ErrorCode expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected.status());
        assertThat(response.getBody().get("errorCode")).isEqualTo(expected.name());
    }

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
