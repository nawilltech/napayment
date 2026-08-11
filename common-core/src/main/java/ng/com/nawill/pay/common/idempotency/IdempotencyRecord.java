package ng.com.nawill.pay.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import ng.com.nawill.pay.common.entity.BaseEntity;

/**
 * The generic, reusable idempotency cache backing {@link Idempotent}. The
 * unique constraint on {@code idempotency_key} (see V0014 migration) is the
 * source of truth per doc 3 §1.2; Redis is only a fast-path optimization on
 * top of it.
 */
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord extends BaseEntity {

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "http_method", nullable = false, length = 8)
    private String httpMethod;

    @Column(name = "request_path", nullable = false, length = 256)
    private String requestPath;

    @Column(name = "response_status")
    private Integer responseStatus;

    // Deliberately not @Lob: Hibernate maps @Lob String to CLOB/oid on
    // Postgres, but the migration (V0014) uses a plain TEXT column.
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, String httpMethod, String requestPath) {
        this.idempotencyKey = idempotencyKey;
        this.httpMethod = httpMethod;
        this.requestPath = requestPath;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void complete(int status, String body) {
        this.responseStatus = status;
        this.responseBody = body;
        this.completedAt = Instant.now();
    }
}
