package ng.com.nawill.pay.onboarding.auth;

/**
 * The reset code is never returned here - it's only ever delivered via the
 * link emailed to the account's own inbox (see {@link AuthService#forgotPassword}).
 * The response is deliberately identical whether or not the email matches an
 * account (doc 3 §2.1's anti-enumeration posture).
 */
public record ForgotPasswordResponse(String message) {
}
