package ng.com.nawill.pay.payments.bankaccount;

import java.util.UUID;
import ng.com.nawill.pay.common.entity.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    Page<BankAccount> findByBusinessId(UUID businessId, Pageable pageable);

    boolean existsByBusinessIdAndBankIdAndAccountNumberAndStatus(UUID businessId, UUID bankId, String accountNumber,
                                                                  EntityStatus status);

    /**
     * Business lives in onboarding-auth-rbac, which payments does not depend
     * on; bank_accounts.business_id already FKs that table, so this read is
     * no new coupling - it just turns an FK violation into a clean 404.
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM business WHERE id = :businessId AND deleted_at IS NULL)",
            nativeQuery = true)
    boolean businessExists(@Param("businessId") UUID businessId);
}
