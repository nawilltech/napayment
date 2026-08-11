package ng.com.nawill.pay.payments.processor;

import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentProcessorService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessorService.class);

    private final PaymentProcessorRepository repository;

    public PaymentProcessorService(PaymentProcessorRepository repository) {
        this.repository = repository;
    }

    public PaymentProcessor onboard(String name) {
        PaymentProcessor processor = repository.save(new PaymentProcessor(name));
        // Privileged admin action - acting admin's userId is already in MDC (doc 3 §7.2).
        log.info("payment processor onboarded: processorId={} name={}", processor.getId(), name);
        return processor;
    }

    @Transactional(readOnly = true)
    public List<PaymentProcessor> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public PaymentProcessor get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment processor not found: " + id));
    }
}
