package ng.com.nawill.pay.common.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as requiring an {@code Idempotency-Key} header
 * (doc 3 §1). {@link IdempotencyAspect} enforces the header, replays a cached
 * response for a completed duplicate, rejects a duplicate that is still
 * in-flight with 409, and persists the eventual response for future replay.
 * <p>
 * The annotated method must return {@code ResponseEntity<?>}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}
