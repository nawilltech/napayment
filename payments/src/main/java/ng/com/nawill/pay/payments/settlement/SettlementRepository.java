package ng.com.nawill.pay.payments.settlement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    Optional<Settlement> findByIdempotencyKey(String idempotencyKey);

    List<Settlement> findByVirtualAccountId(UUID virtualAccountId);
}
