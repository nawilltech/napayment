package ng.com.nawill.pay.onboarding.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * NFR-7: rotating, server-revocable refresh tokens. Redis-only, no DB
 * fallback - same posture as {@link PasswordResetService}: a refresh token
 * is exactly as security-critical as a password-reset code (leak either and
 * an attacker can mint themselves a session), so a Redis outage surfaces as
 * a failure here rather than degrading like {@link LoginAttemptService}'s
 * lockout counters do.
 * <p>
 * Single-use - every {@link #rotate} call revokes the presented token and
 * issues a fresh one (OWASP's recommended refresh-token rotation). The
 * value stored under a token's key is either the owning userId (active) or
 * a short-lived {@code REVOKED:<userId>} tombstone (rotated-out, kept just
 * long enough to catch a near-simultaneous replay) - a plain delete-on-
 * rotate can't distinguish "never existed" from "already used," which is
 * exactly the distinction reuse detection needs. A per-user Redis SET of
 * active token hashes backs "revoke everything for this user" (reuse
 * detected, or a future "log out everywhere").
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final String REVOKED_PREFIX = "REVOKED:";

    private final StringRedisTemplate redisTemplate;
    private final Duration refreshTtl;
    private final Duration reuseDetectionWindow;

    public RefreshTokenService(StringRedisTemplate redisTemplate,
                                @Value("${nawill.auth.jwt.refresh-expiry-days:30}") long refreshExpiryDays,
                                @Value("${nawill.auth.jwt.refresh-reuse-window-minutes:5}") long reuseWindowMinutes) {
        this.redisTemplate = redisTemplate;
        this.refreshTtl = Duration.ofDays(refreshExpiryDays);
        this.reuseDetectionWindow = Duration.ofMinutes(reuseWindowMinutes);
    }

    public String issue(UUID userId) {
        String rawToken = randomToken();
        String hash = hash(rawToken);
        redisTemplate.opsForValue().set(tokenKey(hash), userId.toString(), refreshTtl);
        redisTemplate.opsForSet().add(userSetKey(userId), hash);
        return rawToken;
    }

    public RotationResult rotate(String rawToken) {
        String hash = hash(rawToken);
        String value = redisTemplate.opsForValue().get(tokenKey(hash));

        if (value == null) {
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Invalid refresh token");
        }
        if (value.startsWith(REVOKED_PREFIX)) {
            UUID userId = UUID.fromString(value.substring(REVOKED_PREFIX.length()));
            log.warn("reused refresh token detected, revoking all active tokens: userId={}", userId);
            revokeAllActive(userId);
            throw new UnauthorizedException("REFRESH_TOKEN_REUSED", "This refresh token has already been used");
        }

        UUID userId = UUID.fromString(value);
        redisTemplate.opsForValue().set(tokenKey(hash), REVOKED_PREFIX + userId, reuseDetectionWindow);
        redisTemplate.opsForSet().remove(userSetKey(userId), hash);

        String newRawToken = issue(userId);
        log.info("refresh token rotated: userId={}", userId);
        return new RotationResult(userId, newRawToken);
    }

    public void revoke(String rawToken) {
        String hash = hash(rawToken);
        String value = redisTemplate.opsForValue().get(tokenKey(hash));
        if (value != null && !value.startsWith(REVOKED_PREFIX)) {
            redisTemplate.delete(tokenKey(hash));
            redisTemplate.opsForSet().remove(userSetKey(UUID.fromString(value)), hash);
            log.info("refresh token revoked (logout): userId={}", value);
        }
    }

    private void revokeAllActive(UUID userId) {
        String userSetKey = userSetKey(userId);
        Set<String> hashes = redisTemplate.opsForSet().members(userSetKey);
        if (hashes != null) {
            hashes.forEach(hash -> redisTemplate.delete(tokenKey(hash)));
        }
        redisTemplate.delete(userSetKey);
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String tokenKey(String hash) {
        return AuthRedisConstants.REFRESH_TOKEN_PREFIX + hash;
    }

    private String userSetKey(UUID userId) {
        return AuthRedisConstants.REFRESH_TOKEN_USER_SET_PREFIX + userId;
    }

    public record RotationResult(UUID userId, String rawToken) {
    }
}
