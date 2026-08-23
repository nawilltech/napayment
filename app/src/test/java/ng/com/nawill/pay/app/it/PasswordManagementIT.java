package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Redis-backed login lockout (doc 3 §2.1) and forgot/change-password flows.
 */
class PasswordManagementIT extends AbstractIntegrationTest {

    @Test
    void accountLocksAfterThreeFailedLoginsAndStaysLockedEvenWithCorrectPassword() {
        Map<String, Object> signupRequest = uniqueSignupPayload("Barbara", "Liskov", "SecurePass123!");
        restTemplate.postForEntity(url("/api/v1/auth/signup"), signupRequest, Map.class);
        String email = (String) signupRequest.get("email");

        Map<String, Object> badLogin = Map.of("email", email, "password", "WrongPassword1!");

        ResponseEntity<Map> first = restTemplate.postForEntity(url("/api/v1/auth/login"), badLogin, Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat((String) first.getBody().get("message")).contains("1/3");

        ResponseEntity<Map> second = restTemplate.postForEntity(url("/api/v1/auth/login"), badLogin, Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat((String) second.getBody().get("message")).contains("2/3");

        ResponseEntity<Map> third = restTemplate.postForEntity(url("/api/v1/auth/login"), badLogin, Map.class);
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(third.getBody().get("errorCode")).isEqualTo("ACCOUNT_LOCKED");

        Map<String, Object> correctLogin = Map.of("email", email, "password", "SecurePass123!");
        ResponseEntity<Map> whileLocked = restTemplate.postForEntity(url("/api/v1/auth/login"), correctLogin, Map.class);
        assertThat(whileLocked.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
    }

    @Test
    void forgotPasswordThenResetPasswordAllowsLoginWithNewPasswordOnly() {
        Map<String, Object> signupRequest = uniqueSignupPayload("Katherine", "Goble", "SecurePass123!");
        restTemplate.postForEntity(url("/api/v1/auth/signup"), signupRequest, Map.class);
        String email = (String) signupRequest.get("email");

        ResponseEntity<Map> forgot = restTemplate.postForEntity(
                url("/api/v1/auth/forgot-password"), Map.of("email", email), Map.class);
        assertThat(forgot.getStatusCode()).isEqualTo(HttpStatus.OK);
        String resetToken = (String) forgot.getBody().get("resetToken");
        assertThat(resetToken).matches("\\d{6}");

        Map<String, Object> resetRequest = Map.of(
                "email", email, "token", resetToken, "newPassword", "BrandNewPass1!");
        ResponseEntity<Map> reset = restTemplate.postForEntity(url("/api/v1/auth/reset-password"), resetRequest, Map.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> loginWithNew = restTemplate.postForEntity(
                url("/api/v1/auth/login"), Map.of("email", email, "password", "BrandNewPass1!"), Map.class);
        assertThat(loginWithNew.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginWithNew.getBody().get("accessToken")).isNotNull();

        ResponseEntity<Map> loginWithOld = restTemplate.postForEntity(
                url("/api/v1/auth/login"), Map.of("email", email, "password", "SecurePass123!"), Map.class);
        assertThat(loginWithOld.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void resetPasswordWithWrongTokenIsRejected() {
        Map<String, Object> signupRequest = uniqueSignupPayload("Radia", "Perlman", "SecurePass123!");
        restTemplate.postForEntity(url("/api/v1/auth/signup"), signupRequest, Map.class);
        String email = (String) signupRequest.get("email");

        restTemplate.postForEntity(url("/api/v1/auth/forgot-password"), Map.of("email", email), Map.class);

        Map<String, Object> resetRequest = Map.of(
                "email", email, "token", "000000", "newPassword", "BrandNewPass1!");
        ResponseEntity<Map> reset = restTemplate.postForEntity(url("/api/v1/auth/reset-password"), resetRequest, Map.class);

        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reset.getBody().get("errorCode")).isEqualTo("INVALID_RESET_TOKEN");
    }

    @Test
    void changePasswordRequiresAuthAndCorrectCurrentPassword() {
        String token = signupAndGetToken("Hedy", "Lamarr", "SecurePass123!");

        ResponseEntity<Map> noAuth = restTemplate.postForEntity(
                url("/api/v1/auth/change-password"),
                Map.of("currentPassword", "SecurePass123!", "newPassword", "BrandNewPass1!"), Map.class);
        assertThat(noAuth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpEntity<Map<String, Object>> wrongCurrent = new HttpEntity<>(
                Map.of("currentPassword", "NotTheRealPassword1!", "newPassword", "BrandNewPass1!"), authHeaders(token));
        ResponseEntity<Map> wrongCurrentResponse = restTemplate.postForEntity(
                url("/api/v1/auth/change-password"), wrongCurrent, Map.class);
        assertThat(wrongCurrentResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpEntity<Map<String, Object>> correctCurrent = new HttpEntity<>(
                Map.of("currentPassword", "SecurePass123!", "newPassword", "BrandNewPass1!"), authHeaders(token));
        ResponseEntity<Map> changed = restTemplate.postForEntity(
                url("/api/v1/auth/change-password"), correctCurrent, Map.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void signupRejectsPasswordThatViolatesThePolicy() {
        Map<String, Object> signupRequest = uniqueSignupPayload("Ada", "Yonath", "alllowercase1");
        ResponseEntity<Map> response = restTemplate.postForEntity(url("/api/v1/auth/signup"), signupRequest, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("errorCode")).isEqualTo("VALIDATION_ERROR");
    }
}
