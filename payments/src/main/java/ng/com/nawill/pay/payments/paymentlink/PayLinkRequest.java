package ng.com.nawill.pay.payments.paymentlink;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigInteger;

/** Only used when the link itself has no fixed amount - the payer supplies one. */
/** {@code paymentMethod} is an optional payment_methods code; TRANSFER when omitted (FR-Proc-4). */
public record PayLinkRequest(@Positive BigInteger amount, @Size(max = 32) String paymentMethod) {
}
