package ng.com.nawill.pay.payments.processor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which processor may take a payment for an account (FR-Proc-3/4): active
 * processors, filtered by the business's own setting or the platform
 * default, in priority order. The only place that rule lives - collections
 * route through it and the admin "per business" view reports from it.
 */
@Service
@Transactional(readOnly = true)
public class ProcessorRouter {

    private final PaymentProcessorRepository processorRepository;
    private final BusinessPaymentProcessorRepository settingRepository;

    public ProcessorRouter(PaymentProcessorRepository processorRepository,
                           BusinessPaymentProcessorRepository settingRepository) {
        this.processorRepository = processorRepository;
        this.settingRepository = settingRepository;
    }

    /** Every processor, priority order, with its availability for this account; businessId null = individual. */
    public List<ProcessorAvailability> availabilityFor(UUID businessId) {
        Map<UUID, Boolean> settings = businessId == null ? Map.of()
                : settingRepository.findByBusinessId(businessId).stream()
                        .collect(Collectors.toMap(s -> s.getProcessor().getId(), BusinessPaymentProcessor::isEnabled));
        return processorRepository.findAllByOrderByPriorityAscNameAsc().stream()
                .map(processor -> ProcessorAvailability.of(processor, settings.get(processor.getId())))
                .toList();
    }

    /** The highest-priority available processor offering the method. */
    public PaymentProcessor route(UUID businessId, PaymentMethod method) {
        return availabilityFor(businessId).stream()
                .filter(ProcessorAvailability::available)
                .map(ProcessorAvailability::processor)
                .filter(processor -> processor.offers(method))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_METHOD_UNAVAILABLE, method.label()));
    }

    /** Validates a caller-chosen processor against the same rule. */
    public PaymentProcessor requireUsable(UUID processorId, UUID businessId, PaymentMethod method) {
        ProcessorAvailability availability = availabilityFor(businessId).stream()
                .filter(a -> a.processor().getId().equals(processorId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_PROCESSOR_NOT_FOUND));
        if (!availability.available() || !availability.processor().offers(method)) {
            throw new ApiException(ErrorCode.PAYMENT_PROCESSOR_UNAVAILABLE);
        }
        return availability.processor();
    }

    /** Methods at least one available processor offers, in PaymentMethod order. */
    public List<PaymentMethod> availableMethods(UUID businessId) {
        List<ProcessorAvailability> available = availabilityFor(businessId).stream()
                .filter(ProcessorAvailability::available).toList();
        return Arrays.stream(PaymentMethod.values())
                .filter(method -> available.stream().anyMatch(a -> a.processor().offers(method)))
                .toList();
    }
}
