package ng.com.nawill.pay.payments.paymentlink;

import jakarta.validation.constraints.Positive;
import java.math.BigInteger;

/** Only used when the link itself has no fixed amount - the payer supplies one. */
public record PayLinkRequest(@Positive BigInteger amount) {
}
