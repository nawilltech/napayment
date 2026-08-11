package ng.com.nawill.pay.payments.processor;

import java.util.UUID;
import ng.com.nawill.pay.common.entity.EntityStatus;

public record PaymentProcessorResponse(UUID id, String name, EntityStatus status) {

    public static PaymentProcessorResponse from(PaymentProcessor processor) {
        return new PaymentProcessorResponse(processor.getId(), processor.getName(), processor.getStatus());
    }
}
