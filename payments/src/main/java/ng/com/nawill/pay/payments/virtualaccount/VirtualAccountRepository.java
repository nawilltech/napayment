package ng.com.nawill.pay.payments.virtualaccount;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, UUID> {

    Optional<VirtualAccount> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    List<VirtualAccount> findByUserId(UUID userId);

    List<VirtualAccount> findByBusinessId(UUID businessId);

    Page<VirtualAccount> findByUserId(UUID userId, Pageable pageable);

    Page<VirtualAccount> findByBusinessId(UUID businessId, Pageable pageable);

    /**
     * Row-locked read for any balance-mutating operation (credit, debit,
     * settlement) - prevents the lost-update race where two concurrent
     * transactions both read the same balance before either commits (doc 3
     * §1.4). Always lock this before {@code CollectionAccount} when both
     * are touched in the same transaction, never the reverse.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VirtualAccount v where v.id = :id")
    Optional<VirtualAccount> findByIdForUpdate(UUID id);
}
