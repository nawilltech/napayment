package ng.com.nawill.pay.payments.thirdparty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigInteger;
import ng.com.nawill.pay.payments.processor.PaymentMethod;

/** {@code paymentMethod} is optional; TRANSFER when omitted (FR-Proc-4). */
public record CollectRequest(@NotNull @Positive BigInteger amount, PaymentMethod paymentMethod) {
}
