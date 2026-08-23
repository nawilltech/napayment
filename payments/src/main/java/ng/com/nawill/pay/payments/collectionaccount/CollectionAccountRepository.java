package ng.com.nawill.pay.payments.collectionaccount;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.EntityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CollectionAccountRepository extends JpaRepository<CollectionAccount, UUID> {

    Optional<CollectionAccount> findByStatus(EntityStatus status);

    /**
     * Row-locked read of the single active collection account - always lock
     * this AFTER the VirtualAccount row in any transaction that touches both
     * (never the reverse), so lock ordering is consistent and deadlocking is
     * structurally impossible rather than just unlikely.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CollectionAccount c where c.status = ng.com.nawill.pay.common.entity.EntityStatus.ACTIVE")
    Optional<CollectionAccount> findActiveForUpdate();
}
