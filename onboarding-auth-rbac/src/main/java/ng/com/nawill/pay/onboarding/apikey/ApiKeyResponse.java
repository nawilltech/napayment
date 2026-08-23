package ng.com.nawill.pay.onboarding.apikey;

import java.time.Instant;
import java.util.UUID;

/** Metadata only - never the secret key. */
public record ApiKeyResponse(UUID id, String publicKey, String status, Instant createdAt) {

    public static ApiKeyResponse from(ApiKeyCredential apiKey) {
        return new ApiKeyResponse(apiKey.getId(), apiKey.getPublicKey(),
                apiKey.getStatus().name(), apiKey.getCreatedAt());
    }
}
