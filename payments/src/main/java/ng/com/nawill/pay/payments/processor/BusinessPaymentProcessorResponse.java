package ng.com.nawill.pay.payments.processor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import ng.com.nawill.pay.payments.paymentmethod.PaymentMethod;

/**
 * One processor as it applies to one business (FR-Admin-5): the business's
 * own setting (null = following the default), whether it is available, and
 * which rule decided that.
 */
public record BusinessPaymentProcessorResponse(
        UUID processorId,
        String name,
        String code,
        String logo,
        boolean processorActive,
        boolean defaultEnabled,
        List<PaymentMethodOption> methods,
        Boolean businessSetting,
        boolean available,
        ProcessorAvailability.Source source
) {

    public static BusinessPaymentProcessorResponse from(ProcessorAvailability availability,
                                                        Map<String, PaymentMethod> catalogue) {
        PaymentProcessor processor = availability.processor();
        return new BusinessPaymentProcessorResponse(processor.getId(), processor.getName(), processor.getCode(),
                processor.getLogo(), processor.isActive(), processor.isDefaultEnabled(),
                processor.activeMethods().stream().map(m -> PaymentMethodOption.of(m, true, catalogue)).toList(),
                availability.businessSetting(), availability.available(), availability.source());
    }
}
