package ng.com.nawill.pay.common.ratelimit;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis fixed-window request counter (doc 3 §2.4: "rate limiting per API
 * key / per IP"). A fixed window, not a true token-bucket, is a deliberate
 * simplification (ADR-10, doc 2 §7) - correct without a Lua script, and
 * good enough for this MVP's traffic. Fails open on Redis unavailability:
 * rate limiting is defense-in-depth, not a financial-correctness guarantee
 * like idempotency, so a Redis outage degrades to "unlimited" rather than
 * blocking traffic - same NFR-2 posture as {@code IdempotencyService}.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Returns true if this call is within the limit for the given window. */
    public boolean tryConsume(String identifier, int maxRequests, Duration window) {
        try {
            String key = KEY_PREFIX + identifier;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, window);
            }
            return count != null && count <= maxRequests;
        } catch (Exception e) {
            log.warn("redis rate limit check unavailable, allowing request: {}", e.getMessage());
            return true;
        }
    }
}
