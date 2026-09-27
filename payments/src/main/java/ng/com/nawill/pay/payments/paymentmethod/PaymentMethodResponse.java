package ng.com.nawill.pay.payments.paymentmethod;

import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.EntityStatus;

/** A catalogue entry; {@code processorCount} = processors that list it (deletable only at 0 and unused by transactions). */
public record PaymentMethodResponse(UUID id, String code, String name, String description, int displayOrder,
                                    EntityStatus status, long processorCount, Instant createdAt) {

    static PaymentMethodResponse from(PaymentMethod method, long processorCount) {
        return new PaymentMethodResponse(method.getId(), method.getCode(), method.getName(), method.getDescription(),
                method.getDisplayOrder(), method.getStatus(), processorCount, method.getCreatedAt());
    }
}
