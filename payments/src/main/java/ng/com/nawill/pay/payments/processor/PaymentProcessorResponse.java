package ng.com.nawill.pay.payments.processor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.EntityStatus;

/**
 * {@code businessesSwitchedOn/Off}: how many businesses have their own ON or
 * OFF setting for this processor instead of following {@code defaultEnabled}.
 */
public record PaymentProcessorResponse(
        UUID id,
        String name,
        String code,
        int priority,
        EntityStatus status,
        boolean defaultEnabled,
        List<PaymentMethodResponse> methods,
        long businessesSwitchedOn,
        long businessesSwitchedOff,
        Instant createdAt
) {

    public static PaymentProcessorResponse from(PaymentProcessor processor, long switchedOn, long switchedOff) {
        return new PaymentProcessorResponse(processor.getId(), processor.getName(), processor.getCode(),
                processor.getPriority(), processor.getStatus(), processor.isDefaultEnabled(),
                processor.getMethods().stream().map(m -> PaymentMethodResponse.of(m.getMethod(), m.isActive())).toList(),
                switchedOn, switchedOff, processor.getCreatedAt());
    }
}
