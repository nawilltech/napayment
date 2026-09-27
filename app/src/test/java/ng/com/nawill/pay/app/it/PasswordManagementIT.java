package ng.com.nawill.pay.app.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.onboarding.auth.AuthRedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Redis-backed login lockout (doc 3 §2.1) and forgot/change-password flows.
 */
class PasswordManagementIT extends AbstractIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * The reset code is only ever emailed now (no {@code SmtpEmailGateway}
     * test double captures outbound mail), so tests read it straight out of
     * Redis - the same store {@code PasswordResetService} itself uses.
     */
    private String resetCodeFor(String email) {
        return redisTemplate.opsForValue().get(AuthRedisConstants.PASSWORD_RESET_PREFIX + email);
    }

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
        assertThat(third.getBody().get("errorCode")).isEqualTo(ErrorCode.ACCOUNT_LOCKED.name());

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
        String resetToken = resetCodeFor(email);
        assertThat(resetToken).matches("\\d{6}");

        Map<String, Object> resetRequest = Map.of(
                "email", email, "token", resetToken, "newPassword", "BrandNewPass1!",
                "confirmNewPassword", "BrandNewPass1!");
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
                "email", email, "token", "000000", "newPassword", "BrandNewPass1!",
                "confirmNewPassword", "BrandNewPass1!");
        ResponseEntity<Map> reset = restTemplate.postForEntity(url("/api/v1/auth/reset-password"), resetRequest, Map.class);

        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reset.getBody().get("errorCode")).isEqualTo(ErrorCode.INVALID_RESET_TOKEN.name());
    }

    @Test
    void resetPasswordWithMismatchedConfirmationIsRejected() {
        Map<String, Object> signupRequest = uniqueSignupPayload("Grace", "Hopper", "SecurePass123!");
        restTemplate.postForEntity(url("/api/v1/auth/signup"), signupRequest, Map.class);
        String email = (String) signupRequest.get("email");

        restTemplate.postForEntity(url("/api/v1/auth/forgot-password"), Map.of("email", email), Map.class);
        String resetToken = resetCodeFor(email);

        Map<String, Object> resetRequest = Map.of(
                "email", email, "token", resetToken, "newPassword", "BrandNewPass1!",
                "confirmNewPassword", "SomethingElse1!");
        ResponseEntity<Map> reset = restTemplate.postForEntity(url("/api/v1/auth/reset-password"), resetRequest, Map.class);

        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reset.getBody().get("errorCode")).isEqualTo(ErrorCode.PASSWORD_MISMATCH.name());
    }

    @Test
    void changePasswordRequiresAuthAndCorrectCurrentPassword() {
        String token = signupAndGetToken("Hedy", "Lamarr", "SecurePass123!");

        ResponseEntity<Map> noAuth = restTemplate.postForEntity(
                url("/api/v1/auth/change-password"),
                Map.of("currentPassword", "SecurePass123!", "newPassword", "BrandNewPass1!",
                        "confirmNewPassword", "BrandNewPass1!"), Map.class);
        assertThat(noAuth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpEntity<Map<String, Object>> wrongCurrent = new HttpEntity<>(
                Map.of("currentPassword", "NotTheRealPassword1!", "newPassword", "BrandNewPass1!",
                        "confirmNewPassword", "BrandNewPass1!"), authHeaders(token));
        ResponseEntity<Map> wrongCurrentResponse = restTemplate.postForEntity(
                url("/api/v1/auth/change-password"), wrongCurrent, Map.class);
        assertThat(wrongCurrentResponse.getStatusCode()).isEqualTo(ErrorCode.INCORRECT_CURRENT_PASSWORD.status());
        assertThat(wrongCurrentResponse.getBody().get("errorCode")).isEqualTo(ErrorCode.INCORRECT_CURRENT_PASSWORD.name());

        HttpEntity<Map<String, Object>> mismatchedConfirm = new HttpEntity<>(
                Map.of("currentPassword", "SecurePass123!", "newPassword", "BrandNewPass1!",
                        "confirmNewPassword", "SomethingElse1!"), authHeaders(token));
        ResponseEntity<Map> mismatchedConfirmResponse = restTemplate.postForEntity(
                url("/api/v1/auth/change-password"), mismatchedConfirm, Map.class);
        assertThat(mismatchedConfirmResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mismatchedConfirmResponse.getBody().get("errorCode")).isEqualTo(ErrorCode.PASSWORD_MISMATCH.name());

        HttpEntity<Map<String, Object>> correctCurrent = new HttpEntity<>(
                Map.of("currentPassword", "SecurePass123!", "newPassword", "BrandNewPass1!",
                        "confirmNewPassword", "BrandNewPass1!"), authHeaders(token));
        ResponseEntity<Map> changed = restTemplate.postForEntity(
                url("/api/v1/auth/change-password"), correctCurrent, Map.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * PCI DSS 8.3.7: a new password must not match the current password or
     * any of the account's last 4 passwords. Walks the history past that
     * window to confirm the boundary is exactly 4, not "forever".
     */
    @Test
    void changePasswordRejectsReuseOfCurrentOrRecentPasswordButAllowsOnceItAgesOutOfHistory() {
        String token = signupAndGetToken("Marie", "Curie", "SecurePass123!");

        // Immediate reuse (same as current password) is rejected even before any history exists.
        ResponseEntity<Map> reuseCurrent = changePassword(token, "SecurePass123!", "SecurePass123!");
        assertThat(reuseCurrent.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reuseCurrent.getBody().get("errorCode")).isEqualTo(ErrorCode.PASSWORD_REUSED.name());

        assertThat(changePassword(token, "SecurePass123!", "ChangeOne12!").getStatusCode()).isEqualTo(HttpStatus.OK);

        // The password from signup (now one change back) is still within the last-4 window.
        ResponseEntity<Map> reuseSignupPassword = changePassword(token, "ChangeOne12!", "SecurePass123!");
        assertThat(reuseSignupPassword.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reuseSignupPassword.getBody().get("errorCode")).isEqualTo(ErrorCode.PASSWORD_REUSED.name());

        assertThat(changePassword(token, "ChangeOne12!", "ChangeTwo12!").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changePassword(token, "ChangeTwo12!", "ChangeThr12!").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changePassword(token, "ChangeThr12!", "ChangeFour1!").getStatusCode()).isEqualTo(HttpStatus.OK);

        // History is now [ChangeFour1!, ChangeThr12!, ChangeTwo12!, ChangeOne12!] - the signup
        // password has aged out of the last-4 window, so it's usable again.
        ResponseEntity<Map> reuseAfterAgingOut = changePassword(token, "ChangeFour1!", "SecurePass123!");
        assertThat(reuseAfterAgingOut.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map> changePassword(String token, String currentPassword, String newPassword) {
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                Map.of("currentPassword", currentPassword, "newPassword", newPassword,
                        "confirmNewPassword", newPassword), authHeaders(token));
        return restTemplate.postForEntity(url("/api/v1/auth/change-password"), request, Map.class);
    }

    @Test
    void resetPasswordRejectsReuseOfCurrentPassword() {
        Map<String, Object> signupRequest = uniqueSignupPayload("Rosalind", "Franklin", "SecurePass123!");
        restTemplate.postForEntity(url("/api/v1/auth/signup"), signupRequest, Map.class);
        String email = (String) signupRequest.get("email");

        restTemplate.postForEntity(url("/api/v1/auth/forgot-password"), Map.of("email", email), Map.class);
        String firstToken = resetCodeFor(email);

        Map<String, Object> reuseRequest = Map.of(
                "email", email, "token", firstToken, "newPassword", "SecurePass123!",
                "confirmNewPassword", "SecurePass123!");
        ResponseEntity<Map> reuseResponse = restTemplate.postForEntity(url("/api/v1/auth/reset-password"), reuseRequest, Map.class);
        assertThat(reuseResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reuseResponse.getBody().get("errorCode")).isEqualTo(ErrorCode.PASSWORD_REUSED.name());

        // The one-time code above was consumed by the rejected attempt too - request a fresh one.
        restTemplate.postForEntity(url("/api/v1/auth/forgot-password"), Map.of("email", email), Map.class);
        String secondToken = resetCodeFor(email);
        Map<String, Object> validRequest = Map.of(
                "email", email, "token", secondToken, "newPassword", "BrandNewPass1!",
                "confirmNewPassword", "BrandNewPass1!");
        ResponseEntity<Map> validResponse = restTemplate.postForEntity(url("/api/v1/auth/reset-password"), validRequest, Map.class);
        assertThat(validResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void signupRejectsPasswordThatViolatesThePolicy() {
        Map<String, Object> signupRequest = uniqueSignupPayload("Ada", "Yonath", "alllowercase1");
        ResponseEntity<Map> response = restTemplate.postForEntity(url("/api/v1/auth/signup"), signupRequest, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("errorCode")).isEqualTo(ErrorCode.VALIDATION_ERROR.name());
    }
}
