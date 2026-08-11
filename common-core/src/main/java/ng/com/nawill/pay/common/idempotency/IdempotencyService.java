package ng.com.nawill.pay.common.idempotency;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository repository;
    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(IdempotencyRecordRepository repository, StringRedisTemplate redisTemplate) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> find(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey);
    }

    /**
     * Fast-path race guard. Redis availability is not guaranteed (NFR-2), so a
     * failure here is logged and treated as "not locked" - the DB unique
     * constraint on idempotency_key remains the authoritative guard.
     */
    public boolean tryAcquireRedisLock(String idempotencyKey) {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(IdempotencyConstants.REDIS_KEY_PREFIX + idempotencyKey, "1",
                            IdempotencyConstants.REDIS_LOCK_TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("redis idempotency lock unavailable, falling back to DB constraint only: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Inserts the in-progress marker row. Throws {@link DataIntegrityViolationException}
     * if a concurrent request already holds the DB-level unique constraint on
     * this key - the caller translates that into a 409.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecord createInProgress(String idempotencyKey, String httpMethod, String requestPath) {
        IdempotencyRecord record = new IdempotencyRecord(idempotencyKey, httpMethod, requestPath);
        return repository.saveAndFlush(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(java.util.UUID recordId, int status, String body) {
        repository.findById(recordId).ifPresent(record -> {
            record.complete(status, body);
            repository.save(record);
        });
    }

    /**
     * Removes the in-progress marker after a failed attempt (unexpected
     * exception or 5xx) so a subsequent retry with the same key can be
     * reprocessed rather than being replayed or permanently blocked. This is
     * the one deliberate hard-delete in the platform: idempotency_records is
     * an ephemeral lock/cache table, not one of the audited business entities
     * the soft-delete contract (doc 2 §4.1) applies to.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void discard(java.util.UUID recordId) {
        repository.deleteById(recordId);
        log.info("discarded in-progress idempotency record after failed attempt: recordId={}", recordId);
    }
}
