package ng.com.nawill.pay.payments.paymentlink;

import jakarta.validation.constraints.Positive;
import java.math.BigInteger;
import ng.com.nawill.pay.payments.processor.PaymentMethod;

/** Only used when the link itself has no fixed amount - the payer supplies one. */
/** {@code paymentMethod} is optional; TRANSFER when omitted (FR-Proc-4). */
public record PayLinkRequest(@Positive BigInteger amount, PaymentMethod paymentMethod) {
}
