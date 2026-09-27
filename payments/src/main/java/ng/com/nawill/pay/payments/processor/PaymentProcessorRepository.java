package ng.com.nawill.pay.payments.processor;

import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProcessorRepository extends JpaRepository<PaymentProcessor, UUID> {

    Page<PaymentProcessor> findByNameContainingIgnoreCase(String term, Pageable pageable);

    /** Routing candidates, highest priority (lowest number) first. */
    List<PaymentProcessor> findByStatusOrderByPriorityAscNameAsc(EntityStatus status);

    List<PaymentProcessor> findAllByOrderByPriorityAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsByCode(String code);
}
