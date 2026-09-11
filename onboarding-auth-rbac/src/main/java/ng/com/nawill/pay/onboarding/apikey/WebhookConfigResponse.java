package ng.com.nawill.pay.onboarding.apikey;

import java.time.Instant;

public record WebhookConfigResponse(String callbackUrl, String webhookUrl, Instant updatedAt) {

    public static WebhookConfigResponse from(ApiKeyCredential apiKey) {
        return new WebhookConfigResponse(apiKey.getCallbackUrl(), apiKey.getWebhookUrl(), apiKey.getUpdatedAt());
    }
}
