package ng.com.nawill.pay.payments.thirdparty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigInteger;

/** {@code paymentMethod} is an optional payment_methods code; TRANSFER when omitted (FR-Proc-4). */
public record CollectRequest(@NotNull @Positive BigInteger amount, @Size(max = 32) String paymentMethod) {
}
