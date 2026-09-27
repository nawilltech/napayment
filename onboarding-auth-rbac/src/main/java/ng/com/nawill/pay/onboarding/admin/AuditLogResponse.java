package ng.com.nawill.pay.onboarding.admin;

import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.common.audit.AuditEventType;
import ng.com.nawill.pay.common.audit.AuditOutcome;
import ng.com.nawill.pay.onboarding.audit.SecurityAuditLog;

public record AuditLogResponse(
        UUID id,
        Instant occurredAt,
        AuditEventType eventType,
        AuditOutcome outcome,
        UUID userId,
        UUID businessId,
        String email,
        String ipAddress,
        String userAgent,
        String detail
) {

    public static AuditLogResponse from(SecurityAuditLog log) {
        return new AuditLogResponse(log.getId(), log.getOccurredAt(), log.getEventType(), log.getOutcome(),
                log.getUserId(), log.getBusinessId(), log.getEmail(), log.getIpAddress(), log.getUserAgent(),
                log.getDetail());
    }
}
