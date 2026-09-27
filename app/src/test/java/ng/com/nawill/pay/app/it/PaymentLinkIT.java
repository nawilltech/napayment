package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Shareable payment links, permanent and single-use (FR-14, doc 4 §C.9). */
class PaymentLinkIT extends AbstractIntegrationTest {

    @Test
    void permanentLinkIsPublicAndReusable() {
        String token = businessSignupToken("Ada", "Yonath");

        Map<String, Object> createRequest = Map.of("amount", 2000, "linkType", "PERMANENT", "singleUse", false);
        ResponseEntity<Map> created = createLink(token, createRequest);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String shortCode = (String) created.getBody().get("shortCode");
        assertThat(created.getBody().get("expiresAt")).isNull();

        ResponseEntity<Map> resolved = restTemplate.exchange(
                url("/api/v1/pay/" + shortCode), HttpMethod.GET, HttpEntity.EMPTY, Map.class);
        assertThat(resolved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resolved.getBody().get("linkStatus")).isEqualTo("ACTIVE");

        ResponseEntity<Map> firstPay = pay(shortCode, Map.of());
        assertThat(firstPay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstPay.getBody().get("transactionStatus")).isEqualTo("PAID");

        ResponseEntity<Map> secondPay = pay(shortCode, Map.of());
        assertThat(secondPay.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void singleUseLinkRejectsASecondPayment() {
        String token = businessSignupToken("Rosalind", "Franklin");

        Map<String, Object> createRequest = Map.of("amount", 1500, "linkType", "PERMANENT", "singleUse", true);
        ResponseEntity<Map> created = createLink(token, createRequest);
        String shortCode = (String) created.getBody().get("shortCode");

        ResponseEntity<Map> firstPay = pay(shortCode, Map.of());
        assertThat(firstPay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstPay.getBody().get("transactionStatus")).isEqualTo("PAID");

        ResponseEntity<Map> secondPay = pay(shortCode, Map.of());
        assertThat(secondPay.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(secondPay.getBody().get("errorCode")).isEqualTo(ErrorCode.PAYMENT_LINK_NOT_PAYABLE.name());
    }

    @Test
    void revokedLinkCannotBePaid() {
        String token = businessSignupToken("Marie", "Curie");

        Map<String, Object> createRequest = Map.of("amount", 1000, "linkType", "PERMANENT", "singleUse", false);
        ResponseEntity<Map> created = createLink(token, createRequest);
        String linkId = (String) created.getBody().get("id");
        String shortCode = (String) created.getBody().get("shortCode");

        ResponseEntity<Void> revoke = restTemplate.exchange(
                url("/api/v1/payment-links/" + linkId), HttpMethod.DELETE, new HttpEntity<>(authHeaders(token)), Void.class);
        assertThat(revoke.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> pay = pay(shortCode, Map.of());
        assertThat(pay.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pay.getBody().get("errorCode")).isEqualTo(ErrorCode.PAYMENT_LINK_NOT_PAYABLE.name());
    }

    @Test
    void temporaryLinkWithoutAFixedAmountRequiresThePayerToSupplyOne() {
        String token = businessSignupToken("Chien-Shiung", "Wu");

        Map<String, Object> createRequest = new java.util.HashMap<>();
        createRequest.put("linkType", "TEMPORARY");
        createRequest.put("singleUse", false);
        ResponseEntity<Map> created = createLink(token, createRequest);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("amount")).isNull();
        assertThat(created.getBody().get("expiresAt")).isNotNull();
        String shortCode = (String) created.getBody().get("shortCode");

        ResponseEntity<Map> payWithoutAmount = pay(shortCode, Map.of());
        assertThat(payWithoutAmount.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(payWithoutAmount.getBody().get("errorCode")).isEqualTo(ErrorCode.AMOUNT_REQUIRED.name());

        ResponseEntity<Map> payWithAmount = pay(shortCode, Map.of("amount", 3000));
        assertThat(payWithAmount.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(payWithAmount.getBody().get("transactionStatus")).isEqualTo("PAID");
    }

    private ResponseEntity<Map> createLink(String token, Map<String, Object> request) {
        var headers = authHeaders(token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        return restTemplate.exchange(url("/api/v1/payment-links"), HttpMethod.POST, new HttpEntity<>(request, headers), Map.class);
    }

    private ResponseEntity<Map> pay(String shortCode, Map<String, Object> request) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        return restTemplate.exchange(url("/api/v1/pay/" + shortCode), HttpMethod.POST, new HttpEntity<>(request, headers), Map.class);
    }

    private String businessSignupToken(String firstName, String lastName) {
        Map<String, Object> signupBody = uniqueSignupPayload(firstName, lastName, "SecurePass123!");
        Map<String, Object> businessSignupBody = new java.util.HashMap<>(signupBody);
        businessSignupBody.put("businessName", firstName + " Payments Co");
        businessSignupBody.put("cacNumber", "RC" + UUID.randomUUID().toString().substring(0, 6));
        ResponseEntity<Map> response = restTemplate.postForEntity(url("/api/v1/auth/signup"), businessSignupBody, Map.class);
        return (String) response.getBody().get("accessToken");
    }
}
