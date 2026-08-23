package ng.com.nawill.pay.payments.bankaccount;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    List<BankAccount> findByBusinessId(UUID businessId);
}
