package ng.com.nawill.pay.onboarding.auth;

import java.security.SecureRandom;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-only, one-time-use 6-digit password-reset codes. Unlike the login
 * lockout counters, a Redis failure here is not degraded/fail-open - there is
 * no other place this token can live, so callers see the failure (translated
 * to a 500 by the generic exception handler) rather than silently issuing an
 * unusable reset.
 */
@Service
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final Duration tokenTtl;

    public PasswordResetService(StringRedisTemplate redisTemplate,
                                 @Value("${nawill.auth.password-reset.token-ttl-minutes:15}") long tokenTtlMinutes) {
        this.redisTemplate = redisTemplate;
        this.tokenTtl = Duration.ofMinutes(tokenTtlMinutes);
    }

    public String issueToken(String email) {
        String token = String.format("%06d", RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(key(email), token, tokenTtl);
        return token;
    }

    /** Validates the token and, on success, consumes it (one-time use). */
    public boolean validateAndConsume(String email, String token) {
        String stored = redisTemplate.opsForValue().get(key(email));
        if (stored == null || !stored.equals(token)) {
            return false;
        }
        redisTemplate.delete(key(email));
        return true;
    }

    private String key(String email) {
        return AuthRedisConstants.PASSWORD_RESET_PREFIX + email;
    }
}
