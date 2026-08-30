package ng.com.nawill.pay.payments.dynamicaccount;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DynamicVirtualAccountRepository extends JpaRepository<DynamicVirtualAccount, UUID> {

    boolean existsByAccountNumber(String accountNumber);

    Page<DynamicVirtualAccount> findByBusinessId(UUID businessId, Pageable pageable);

    /** Locked for the whole eligibility-check-then-deposit so a second simulated deposit can't land after the account is already PAID. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DynamicVirtualAccount d where d.accountNumber = :accountNumber")
    Optional<DynamicVirtualAccount> findByAccountNumberForUpdate(String accountNumber);
}
