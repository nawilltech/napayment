package ng.com.nawill.pay.common.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import ng.com.nawill.pay.common.exception.BadRequestException;
import ng.com.nawill.pay.common.exception.ConflictException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class IdempotencyAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyAspect.class);

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public IdempotencyAspect(IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(ng.com.nawill.pay.common.idempotency.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = currentRequest();
        String key = request.getHeader(IdempotencyConstants.HEADER);
        if (key == null || key.isBlank()) {
            throw new BadRequestException("IDEMPOTENCY_KEY_REQUIRED",
                    "The '" + IdempotencyConstants.HEADER + "' header is required for this operation");
        }

        var existing = idempotencyService.find(key);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (record.getResponseStatus() != null) {
                log.info("replaying cached response for idempotency key");
                return replay(record);
            }
            throw new ConflictException("A request with this idempotency key is still processing");
        }

        idempotencyService.tryAcquireRedisLock(key);

        IdempotencyRecord record;
        try {
            record = idempotencyService.createInProgress(key, request.getMethod(), request.getRequestURI());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A request with this idempotency key is still processing");
        }

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            idempotencyService.discard(record.getId());
            throw t;
        }

        if (result instanceof ResponseEntity<?> response) {
            if (response.getStatusCode().is5xxServerError()) {
                idempotencyService.discard(record.getId());
            } else {
                idempotencyService.complete(record.getId(), response.getStatusCode().value(),
                        objectMapper.writeValueAsString(response.getBody()));
            }
        } else {
            // Annotated methods must return ResponseEntity<?> - nothing to cache otherwise.
            idempotencyService.discard(record.getId());
        }
        return result;
    }

    private ResponseEntity<JsonNode> replay(IdempotencyRecord record) throws Exception {
        JsonNode body = objectMapper.readTree(record.getResponseBody());
        return ResponseEntity.status(record.getResponseStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private HttpServletRequest currentRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}
