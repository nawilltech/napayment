package ng.com.nawill.pay.onboarding.apikey;

/** Both optional; either may be blank to clear it. */
public record WebhookConfigRequest(String callbackUrl, String webhookUrl) {
}
