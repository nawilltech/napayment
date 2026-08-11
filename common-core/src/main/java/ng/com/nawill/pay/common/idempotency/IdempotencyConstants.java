package ng.com.nawill.pay.common.idempotency;

import java.time.Duration;

public final class IdempotencyConstants {

    public static final String HEADER = "Idempotency-Key";
    public static final Duration REDIS_LOCK_TTL = Duration.ofHours(24);
    public static final String REDIS_KEY_PREFIX = "idempotency:lock:";

    private IdempotencyConstants() {
    }
}
