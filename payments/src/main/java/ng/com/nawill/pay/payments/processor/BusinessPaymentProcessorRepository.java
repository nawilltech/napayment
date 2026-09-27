package ng.com.nawill.pay.payments.processor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessPaymentProcessorRepository extends JpaRepository<BusinessPaymentProcessor, UUID> {

    List<BusinessPaymentProcessor> findByBusinessId(UUID businessId);

    Optional<BusinessPaymentProcessor> findByBusinessIdAndProcessorId(UUID businessId, UUID processorId);

    long countByProcessorIdAndEnabled(UUID processorId, boolean enabled);

    @Modifying
    @Query("DELETE FROM BusinessPaymentProcessor s WHERE s.processor.id = :processorId")
    int deleteAllForProcessor(@Param("processorId") UUID processorId);
}
