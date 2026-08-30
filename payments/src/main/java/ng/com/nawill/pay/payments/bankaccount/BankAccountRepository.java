package ng.com.nawill.pay.payments.bankaccount;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    Page<BankAccount> findByBusinessId(UUID businessId, Pageable pageable);
}
