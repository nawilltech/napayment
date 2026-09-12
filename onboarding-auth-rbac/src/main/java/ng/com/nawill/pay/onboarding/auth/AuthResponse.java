package ng.com.nawill.pay.onboarding.auth;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        UUID businessId
) {

    public static AuthResponse bearer(String accessToken, String refreshToken, long expiresInSeconds, UUID userId,
                                       UUID businessId) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, userId, businessId);
    }
}
