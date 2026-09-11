package ng.com.nawill.pay.payments.processor;

import java.util.Optional;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProcessorRepository extends JpaRepository<PaymentProcessor, UUID> {

    Optional<PaymentProcessor> findFirstByStatus(EntityStatus status);

    Page<PaymentProcessor> findByNameContainingIgnoreCase(String term, Pageable pageable);
}
