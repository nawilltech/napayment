package ng.com.nawill.pay.onboarding.auth;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-only login-attempt counter and account lockout (doc 3 §2.1). Unlike
 * {@code IdempotencyService}, there is no DB fallback here - lockout state is
 * genuinely ephemeral and lives in Redis alone. Redis calls are still
 * defensively wrapped: a Redis outage degrades to "treat as not locked" /
 * "don't count this failure" rather than failing the whole login endpoint,
 * logged as a warning so the degraded state is visible in ops.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final StringRedisTemplate redisTemplate;
    private final int maxAttempts;
    private final Duration lockoutDuration;

    public LoginAttemptService(StringRedisTemplate redisTemplate,
                                @Value("${nawill.auth.lockout.max-attempts:3}") int maxAttempts,
                                @Value("${nawill.auth.lockout.duration-minutes:15}") long lockoutDurationMinutes) {
        this.redisTemplate = redisTemplate;
        this.maxAttempts = maxAttempts;
        this.lockoutDuration = Duration.ofMinutes(lockoutDurationMinutes);
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public Duration lockoutDuration() {
        return lockoutDuration;
    }

    public Optional<Duration> lockedRemaining(String email) {
        try {
            Long ttl = redisTemplate.getExpire(lockoutKey(email), TimeUnit.SECONDS);
            if (ttl == null || ttl < 0) {
                return Optional.empty();
            }
            return Optional.of(Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("redis lockout check unavailable, treating account as not locked: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Increments the failure counter, starting a fresh {@link #lockoutDuration}
     * window on the first failure so the attempt window and the lockout
     * window match. Returns the new count, or 0 if Redis is unavailable
     * (meaning this failure isn't counted toward lockout at all).
     */
    public int recordFailure(String email) {
        try {
            String key = attemptsKey(email);
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, lockoutDuration);
            }
            return count == null ? 0 : count.intValue();
        } catch (Exception e) {
            log.warn("redis login-attempt tracking unavailable, skipping lockout enforcement: {}", e.getMessage());
            return 0;
        }
    }

    public void lock(String email) {
        try {
            redisTemplate.opsForValue().set(lockoutKey(email), "1", lockoutDuration);
        } catch (Exception e) {
            log.warn("redis lockout write unavailable: {}", e.getMessage());
        }
    }

    public void clear(String email) {
        try {
            redisTemplate.delete(List.of(attemptsKey(email), lockoutKey(email)));
        } catch (Exception e) {
            log.warn("redis lockout state cleanup unavailable: {}", e.getMessage());
        }
    }

    private String attemptsKey(String email) {
        return AuthRedisConstants.LOGIN_ATTEMPTS_PREFIX + email;
    }

    private String lockoutKey(String email) {
        return AuthRedisConstants.LOGIN_LOCKOUT_PREFIX + email;
    }
}
