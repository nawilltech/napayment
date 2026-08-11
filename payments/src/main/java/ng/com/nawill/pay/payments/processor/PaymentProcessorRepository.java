package ng.com.nawill.pay.payments.processor;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProcessorRepository extends JpaRepository<PaymentProcessor, UUID> {
}
