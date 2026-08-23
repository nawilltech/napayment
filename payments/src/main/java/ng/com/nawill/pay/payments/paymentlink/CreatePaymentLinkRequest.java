package ng.com.nawill.pay.payments.paymentlink;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigInteger;
import java.time.Instant;

/**
 * {@code amount} null means the payer enters their own amount at pay time.
 * {@code expiresAt} is required for TEMPORARY links (defaults to +24h if
 * omitted) and must be absent for PERMANENT ones.
 */
public record CreatePaymentLinkRequest(
        @Positive BigInteger amount,
        @NotNull PaymentLinkType linkType,
        Instant expiresAt,
        @NotNull Boolean singleUse
) {
}
