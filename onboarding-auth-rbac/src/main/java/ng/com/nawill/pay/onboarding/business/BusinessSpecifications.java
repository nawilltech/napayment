package ng.com.nawill.pay.onboarding.business;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import ng.com.nawill.pay.common.entity.EntityStatus;
import org.springframework.data.jpa.domain.Specification;

/** Filters for the platform business list (FR-3 admin console). */
public final class BusinessSpecifications {

    private BusinessSpecifications() {
    }

    /** {@code term} matches the business name or CAC number, case-insensitively. */
    public static Specification<Business> matching(String term, KycStatus kycStatus, EntityStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (kycStatus != null) {
                predicates.add(cb.equal(root.get("kycStatus"), kycStatus));
            }
            if (term != null && !term.isBlank()) {
                String like = "%" + term.trim().toLowerCase() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("cacNumber")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
