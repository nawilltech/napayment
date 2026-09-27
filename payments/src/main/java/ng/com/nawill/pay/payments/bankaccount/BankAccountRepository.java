package ng.com.nawill.pay.payments.bankaccount;

import java.util.UUID;
import ng.com.nawill.pay.common.entity.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    Page<BankAccount> findByBusinessId(UUID businessId, Pageable pageable);

    boolean existsByBusinessIdAndBankIdAndAccountNumberAndStatus(UUID businessId, UUID bankId, String accountNumber,
                                                                  EntityStatus status);
}
