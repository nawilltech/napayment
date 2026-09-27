package ng.com.nawill.pay.payments.processor;

import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProcessorRepository extends JpaRepository<PaymentProcessor, UUID> {

    Page<PaymentProcessor> findByArchivedAtIsNull(Pageable pageable);

    Page<PaymentProcessor> findByArchivedAtIsNotNull(Pageable pageable);

    Page<PaymentProcessor> findByArchivedAtIsNullAndNameContainingIgnoreCase(String term, Pageable pageable);

    Page<PaymentProcessor> findByArchivedAtIsNotNullAndNameContainingIgnoreCase(String term, Pageable pageable);

    /** Routing candidates, highest priority (lowest number) first. */
    List<PaymentProcessor> findByStatusOrderByPriorityAscNameAsc(EntityStatus status);

    /** Every processor that isn't archived, priority order - routing and per-business views. */
    List<PaymentProcessor> findByArchivedAtIsNullOrderByPriorityAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsByCode(String code);
}
