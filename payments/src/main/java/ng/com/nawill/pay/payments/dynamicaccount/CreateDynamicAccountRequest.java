package ng.com.nawill.pay.payments.dynamicaccount;

import jakarta.validation.constraints.Positive;
import java.math.BigInteger;
import java.time.Instant;

/** {@code expiresAt} defaults to +30 minutes if omitted - typical checkout-window length. */
public record CreateDynamicAccountRequest(
        @Positive BigInteger expectedAmount,
        Instant expiresAt,
        String reference
) {
}
