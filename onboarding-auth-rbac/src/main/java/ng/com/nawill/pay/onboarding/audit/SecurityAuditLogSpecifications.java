package ng.com.nawill.pay.onboarding.audit;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Filters for the platform audit-log viewer (doc 4 §C.5). Every filter is optional. */
public final class SecurityAuditLogSpecifications {

    private SecurityAuditLogSpecifications() {
    }

    public static Specification<SecurityAuditLog> matching(AuditEventType eventType, AuditOutcome outcome,
                                                            UUID userId, UUID businessId, String email,
                                                            Instant fromDate, Instant toDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (eventType != null) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            if (outcome != null) {
                predicates.add(cb.equal(root.get("outcome"), outcome));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (businessId != null) {
                predicates.add(cb.equal(root.get("businessId"), businessId));
            }
            if (email != null && !email.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("email")), "%" + email.trim().toLowerCase() + "%"));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), toDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
