package ng.com.nawill.pay.payments.processor;

import java.util.List;
import java.util.UUID;

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
        List<PaymentMethodResponse> methods,
        Boolean businessSetting,
        boolean available,
        ProcessorAvailability.Source source
) {

    public static BusinessPaymentProcessorResponse from(ProcessorAvailability availability) {
        PaymentProcessor processor = availability.processor();
        return new BusinessPaymentProcessorResponse(processor.getId(), processor.getName(), processor.getCode(),
                processor.getLogo(), processor.isActive(), processor.isDefaultEnabled(),
                processor.activeMethods().stream().map(m -> PaymentMethodResponse.of(m, true)).toList(),
                availability.businessSetting(), availability.available(), availability.source());
    }
}
