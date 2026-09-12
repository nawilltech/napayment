package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import ng.com.nawill.pay.onboarding.audit.AuditEventType;
import ng.com.nawill.pay.onboarding.audit.AuditOutcome;
import ng.com.nawill.pay.onboarding.audit.SecurityAuditLog;
import ng.com.nawill.pay.onboarding.audit.SecurityAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * PCI DSS 10.2.1/10.3.1: auth and onboarding activity must be durably
 * logged with user id, event type, outcome, and origin - not just as
 * ephemeral console output.
 */
class SecurityAuditLogIT extends AbstractIntegrationTest {

    @Autowired
    private SecurityAuditLogRepository securityAuditLogRepository;

    @Test
    void signupWritesADurableSuccessEntry() {
        Map<String, Object> signup = signupResponse("Audit", "SignupCase");
        String userId = (String) signup.get("userId");

        List<SecurityAuditLog> entries = securityAuditLogRepository.findAll();
        SecurityAuditLog entry = entries.stream()
                .filter(e -> e.getEventType() == AuditEventType.SIGNUP && userId.equals(str(e.getUserId())))
                .findFirst().orElseThrow(() -> new AssertionError("No SIGNUP audit entry for userId=" + userId));

        assertThat(entry.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(entry.getEmail()).isNotNull();
        assertThat(entry.getOccurredAt()).isNotNull();
    }

    @Test
    void failedLoginWritesAFailureEntryWithNoUserIdForAnUnknownEmail() {
        String unknownEmail = "nobody-" + System.nanoTime() + "@example.com";

        restTemplate.postForEntity(url("/api/v1/auth/login"), Map.of("email", unknownEmail, "password", "whatever"), Map.class);

        List<SecurityAuditLog> entries = securityAuditLogRepository.findAll();
        SecurityAuditLog entry = entries.stream()
                .filter(e -> e.getEventType() == AuditEventType.LOGIN && unknownEmail.equals(e.getEmail()))
                .findFirst().orElseThrow(() -> new AssertionError("No LOGIN audit entry for " + unknownEmail));

        assertThat(entry.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(entry.getUserId()).isNull();
    }

    @Test
    void successfulLoginWritesASuccessEntry() {
        Map<String, Object> body = uniqueSignupPayload("AuditLogin", "Case", "SecurePass123!");
        ResponseEntity<Map> signupResponse = restTemplate.postForEntity(url("/api/v1/auth/signup"), body, Map.class);
        String userId = (String) signupResponse.getBody().get("userId");

        restTemplate.exchange(url("/api/v1/auth/login"), HttpMethod.POST,
                new HttpEntity<>(Map.of("email", body.get("email"), "password", body.get("password"))), Map.class);

        List<SecurityAuditLog> entries = securityAuditLogRepository.findAll();
        boolean hasSuccessfulLogin = entries.stream()
                .anyMatch(e -> e.getEventType() == AuditEventType.LOGIN && e.getOutcome() == AuditOutcome.SUCCESS
                        && userId.equals(str(e.getUserId())));

        assertThat(hasSuccessfulLogin).isTrue();
    }

    private Map<String, Object> signupResponse(String firstName, String lastName) {
        Map<String, Object> body = uniqueSignupPayload(firstName, lastName, "SecurePass123!");
        return restTemplate.postForEntity(url("/api/v1/auth/signup"), body, Map.class).getBody();
    }

    private static String str(Object uuid) {
        return uuid == null ? null : uuid.toString();
    }
}
