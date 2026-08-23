package ng.com.nawill.pay.payments.settlement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementAccountRepository extends JpaRepository<SettlementAccount, UUID> {

    List<SettlementAccount> findByVirtualAccountId(UUID virtualAccountId);
}
