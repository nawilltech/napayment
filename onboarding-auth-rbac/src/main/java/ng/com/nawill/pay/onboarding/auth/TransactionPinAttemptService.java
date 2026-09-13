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
 * Redis-only attempt counter and lockout for the transaction PIN (FR-Auth-2),
 * keyed by {@code userId} rather than email - the same shape as
 * {@link LoginAttemptService}, but scoped separately and tuned stricter: a
 * 4-digit PIN has only 10,000 possibilities, materially easier to
 * brute-force than a password, and a wrong PIN should lock only the
 * transfer capability, never login itself.
 */
@Service
public class TransactionPinAttemptService {

    private static final Logger log = LoggerFactory.getLogger(TransactionPinAttemptService.class);

    private final StringRedisTemplate redisTemplate;
    private final int maxAttempts;
    private final Duration lockoutDuration;

    public TransactionPinAttemptService(StringRedisTemplate redisTemplate,
                                         @Value("${nawill.auth.transaction-pin.max-attempts:3}") int maxAttempts,
                                         @Value("${nawill.auth.transaction-pin.lockout-duration-minutes:30}") long lockoutDurationMinutes) {
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

    public Optional<Duration> lockedRemaining(String userId) {
        try {
            Long ttl = redisTemplate.getExpire(lockoutKey(userId), TimeUnit.SECONDS);
            if (ttl == null || ttl < 0) {
                return Optional.empty();
            }
            return Optional.of(Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("redis pin-lockout check unavailable, treating as not locked: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public int recordFailure(String userId) {
        try {
            String key = attemptsKey(userId);
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, lockoutDuration);
            }
            return count == null ? 0 : count.intValue();
        } catch (Exception e) {
            log.warn("redis pin-attempt tracking unavailable, skipping lockout enforcement: {}", e.getMessage());
            return 0;
        }
    }

    public void lock(String userId) {
        try {
            redisTemplate.opsForValue().set(lockoutKey(userId), "1", lockoutDuration);
        } catch (Exception e) {
            log.warn("redis pin-lockout write unavailable: {}", e.getMessage());
        }
    }

    public void clear(String userId) {
        try {
            redisTemplate.delete(List.of(attemptsKey(userId), lockoutKey(userId)));
        } catch (Exception e) {
            log.warn("redis pin-lockout state cleanup unavailable: {}", e.getMessage());
        }
    }

    private String attemptsKey(String userId) {
        return AuthRedisConstants.TRANSACTION_PIN_ATTEMPTS_PREFIX + userId;
    }

    private String lockoutKey(String userId) {
        return AuthRedisConstants.TRANSACTION_PIN_LOCKOUT_PREFIX + userId;
    }
}
