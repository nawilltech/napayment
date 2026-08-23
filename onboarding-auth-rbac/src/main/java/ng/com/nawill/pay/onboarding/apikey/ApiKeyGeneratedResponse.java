package ng.com.nawill.pay.onboarding.apikey;

import java.util.UUID;

/**
 * Returned only from generate/regenerate - {@code secretKey} is shown here
 * in plaintext exactly once and is never retrievable again afterward.
 */
public record ApiKeyGeneratedResponse(UUID id, String publicKey, String secretKey) {
}
