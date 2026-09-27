package ng.com.nawill.pay.payments.transaction;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccount;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the single {@link Specification} that both the paginated list and
 * the analytics endpoint filter against - keeps row-level ownership scoping
 * (mirrors {@code TransactionService#assertOwnership}) and every optional
 * query filter in one place instead of two.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> build(TransactionFilter filter, CurrentUser currentUser) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!currentUser.isSuperAdmin()) {
                Join<Transaction, VirtualAccount> virtualAccount = root.join("virtualAccount");
                Predicate ownedByUser = cb.equal(virtualAccount.get("userId"), currentUser.userId());
                predicates.add(currentUser.hasBusinessScope()
                        ? cb.or(ownedByUser, cb.equal(virtualAccount.get("businessId"), currentUser.businessId()))
                        : ownedByUser);
            }
            if (filter.virtualAccountId() != null) {
                predicates.add(cb.equal(root.get("virtualAccount").get("id"), filter.virtualAccountId()));
            }
            if (filter.businessId() != null) {
                predicates.add(cb.equal(root.get("virtualAccount").get("businessId"), filter.businessId()));
            }
            if (filter.startDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.startDate()));
            }
            if (filter.endDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.endDate()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("transactionStatus"), filter.status()));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("transactionType"), filter.type()));
            }
            if (filter.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.maxAmount()));
            }
            if (filter.term() != null && !filter.term().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("sessionId")), "%" + filter.term().trim().toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
