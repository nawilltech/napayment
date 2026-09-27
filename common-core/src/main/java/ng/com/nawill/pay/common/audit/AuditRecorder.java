package ng.com.nawill.pay.common.audit;

import java.util.UUID;

/**
 * Writes to the security audit log (FR-Admin-3) from any module.
 * Implemented by onboarding-auth-rbac's SecurityAuditService; modules that
 * cannot depend on it (payments) take this interface instead. Never throws -
 * a failed audit write is logged, it does not fail the business action.
 */
public interface AuditRecorder {

    void record(AuditEventType eventType, AuditOutcome outcome, UUID userId, UUID businessId, String detail);
}
