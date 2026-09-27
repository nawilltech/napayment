package ng.com.nawill.pay.onboarding.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.common.audit.AuditEventType;
import ng.com.nawill.pay.common.audit.AuditOutcome;
import org.hibernate.annotations.UuidGenerator;

/**
 * PCI DSS 10.2.1/10.3.1/10.5.5: an append-only auth/onboarding activity
 * record. Deliberately does NOT extend {@code BaseEntity} - that base class
 * carries soft-delete (a {@code deletedAt} marker) and a mutable
 * {@code status}/{@code updatedAt}, semantics for a domain record that gets
 * edited over its lifetime. An audit log entry is the opposite: it is
 * written once and never touched again. {@link SecurityAuditLogRepository}
 * exposes no update/delete method anywhere - that is the practical,
 * application-level version of "immutable once written" a single Postgres
 * instance can offer without WORM storage, a restricted DB role that
 * revokes UPDATE/DELETE on this table, or shipping to an external
 * log store/SIEM, all real production-hardening steps beyond what
 * application code alone can guarantee.
 */
@Entity
@Table(name = "security_audit_log")
public class SecurityAuditLog {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 48, updatable = false)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16, updatable = false)
    private AuditOutcome outcome;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "business_id", updatable = false)
    private UUID businessId;

    @Column(name = "email", length = 128, updatable = false)
    private String email;

    @Column(name = "ip_address", length = 64, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 256, updatable = false)
    private String userAgent;

    @Column(name = "detail", length = 512, updatable = false)
    private String detail;

    protected SecurityAuditLog() {
    }

    public SecurityAuditLog(AuditEventType eventType, AuditOutcome outcome, UUID userId, UUID businessId,
                             String email, String ipAddress, String userAgent, String detail) {
        this.eventType = eventType;
        this.outcome = outcome;
        this.userId = userId;
        this.businessId = businessId;
        this.email = email;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.detail = detail;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getEmail() {
        return email;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getDetail() {
        return detail;
    }
}
