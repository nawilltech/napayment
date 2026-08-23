package ng.com.nawill.pay.payments.dynamicaccount;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DynamicVirtualAccountRepository extends JpaRepository<DynamicVirtualAccount, UUID> {

    boolean existsByAccountNumber(String accountNumber);

    List<DynamicVirtualAccount> findByBusinessId(UUID businessId);

    /** Locked for the whole eligibility-check-then-deposit so a second simulated deposit can't land after the account is already PAID. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DynamicVirtualAccount d where d.accountNumber = :accountNumber")
    Optional<DynamicVirtualAccount> findByAccountNumberForUpdate(String accountNumber);
}
