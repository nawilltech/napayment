package ng.com.nawill.pay.onboarding.auth;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        UUID businessId
) {

    public static AuthResponse bearer(String accessToken, long expiresInSeconds, UUID userId, UUID businessId) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, userId, businessId);
    }
}
