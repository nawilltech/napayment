package ng.com.nawill.pay.onboarding.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records auth/onboarding activity for PCI DSS 10.2.1 coverage (failed
 * logins, credential/account changes, KYC activity). Every write runs in
 * its OWN transaction ({@code REQUIRES_NEW}), not the caller's - most
 * FAILURE-outcome calls happen right before the calling method throws
 * (e.g. {@code AuthService.login}'s invalid-credentials path), and without
 * this the audit row would be rolled back along with everything else the
 * moment that exception unwinds the caller's own {@code @Transactional}.
 * A failure to write an audit entry must still never break or mask the
 * real operation it's describing - every write is also wrapped and logged
 * at WARN on failure, never rethrown, the same defensive posture {@code
 * LoginAttemptService} already uses for its own Redis calls.
 */
@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);
    private static final int EMAIL_MAX = 128;
    private static final int USER_AGENT_MAX = 256;
    private static final int DETAIL_MAX = 512;

    private final SecurityAuditLogRepository securityAuditLogRepository;

    public SecurityAuditService(SecurityAuditLogRepository securityAuditLogRepository) {
        this.securityAuditLogRepository = securityAuditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEventType eventType, AuditOutcome outcome, UUID userId, UUID businessId, String email,
                        String detail) {
        try {
            SecurityAuditLog entry = new SecurityAuditLog(eventType, outcome, userId, businessId,
                    truncate(email, EMAIL_MAX), currentIpAddress(), truncate(currentUserAgent(), USER_AGENT_MAX),
                    truncate(detail, DETAIL_MAX));
            securityAuditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("failed to write security audit log entry: eventType={} error={}", eventType, e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEventType eventType, AuditOutcome outcome, UUID userId, UUID businessId, String detail) {
        record(eventType, outcome, userId, businessId, null, detail);
    }

    private String currentIpAddress() {
        return currentRequest().map(HttpServletRequest::getRemoteAddr).orElse(null);
    }

    private String currentUserAgent() {
        return currentRequest().map(r -> r.getHeader("User-Agent")).orElse(null);
    }

    private Optional<HttpServletRequest> currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return Optional.of(servletAttributes.getRequest());
        }
        return Optional.empty();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
