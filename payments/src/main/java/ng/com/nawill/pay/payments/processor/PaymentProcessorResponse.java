package ng.com.nawill.pay.payments.processor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.EntityStatus;
import ng.com.nawill.pay.payments.paymentmethod.PaymentMethod;

/**
 * {@code businessesSwitchedOn/Off}: how many businesses have their own ON or
 * OFF setting for this processor instead of following {@code defaultEnabled}.
 */
public record PaymentProcessorResponse(
        UUID id,
        String name,
        String code,
        /** Base64 data URL, or null when the processor has no logo. */
        String logo,
        int priority,
        EntityStatus status,
        boolean defaultEnabled,
        List<PaymentMethodOption> methods,
        long businessesSwitchedOn,
        long businessesSwitchedOff,
        Instant createdAt
) {

    public static PaymentProcessorResponse from(PaymentProcessor processor, long switchedOn, long switchedOff,
                                                Map<String, PaymentMethod> catalogue) {
        return new PaymentProcessorResponse(processor.getId(), processor.getName(), processor.getCode(),
                processor.getLogo(), processor.getPriority(), processor.getStatus(), processor.isDefaultEnabled(),
                processor.getMethods().stream()
                        .map(m -> PaymentMethodOption.of(m.getMethod(), m.isActive(), catalogue)).toList(),
                switchedOn, switchedOff, processor.getCreatedAt());
    }
}
