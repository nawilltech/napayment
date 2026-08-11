package ng.com.nawill.pay.payments.virtualaccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, UUID> {

    Optional<VirtualAccount> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    List<VirtualAccount> findByUserId(UUID userId);

    List<VirtualAccount> findByBusinessId(UUID businessId);
}
