package ng.com.nawill.pay.payments.paymentlink;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PaymentLinkRepository extends JpaRepository<PaymentLink, UUID> {

    Optional<PaymentLink> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    List<PaymentLink> findByBusinessId(UUID businessId);

    /** Locked for the whole eligibility-check-then-redeem so two concurrent pay attempts on a single-use link can't both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentLink p where p.shortCode = :shortCode")
    Optional<PaymentLink> findByShortCodeForUpdate(String shortCode);
}
