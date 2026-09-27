package ng.com.nawill.pay.onboarding.admin;

import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.common.audit.AuditEventType;
import ng.com.nawill.pay.common.audit.AuditOutcome;
import ng.com.nawill.pay.onboarding.audit.SecurityAuditLogRepository;
import ng.com.nawill.pay.onboarding.audit.SecurityAuditLogSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Doc 4 §C.5: read-only view of the security audit trail, newest first. The
 * log itself stays append-only (SecurityAuditLogRepository exposes no
 * update/delete) - this only reads it.
 */
@Service
@Transactional(readOnly = true)
public class AdminAuditLogService {

    private final SecurityAuditLogRepository securityAuditLogRepository;

    public AdminAuditLogService(SecurityAuditLogRepository securityAuditLogRepository) {
        this.securityAuditLogRepository = securityAuditLogRepository;
    }

    public Page<AuditLogResponse> list(AuditEventType eventType, AuditOutcome outcome, UUID userId, UUID businessId,
                                       String email, Instant fromDate, Instant toDate, int page, int size) {
        return securityAuditLogRepository.findAll(
                SecurityAuditLogSpecifications.matching(eventType, outcome, userId, businessId, email, fromDate, toDate),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"))).map(AuditLogResponse::from);
    }
}
