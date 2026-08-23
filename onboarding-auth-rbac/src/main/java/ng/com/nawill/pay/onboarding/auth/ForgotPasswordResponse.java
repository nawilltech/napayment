package ng.com.nawill.pay.onboarding.auth;

/**
 * {@code resetToken} is only populated when the email matches an account.
 * Returned directly in the response (rather than emailed/texted) because
 * this MVP has no email/SMS dispatch integration yet - mirrors the sandbox
 * stand-ins used elsewhere (e.g. SandboxPaymentProcessorGateway).
 * TODO(FR-Notif-1): deliver via email/SMS instead of the API response.
 */
public record ForgotPasswordResponse(String message, String resetToken) {
}
