package ng.com.nawill.pay.payments.transfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigInteger;

/**
 * {@code recipientIdentifier} is format-sniffed, not a separate field per
 * method: a 10-digit numeric string resolves as a Nawill account number
 * (NUBAN-style, see {@code AccountNumberGenerator}), anything else resolves
 * as a phone number via {@link RecipientDirectory}.
 */
public record TransferRequest(
        @NotBlank String recipientIdentifier,
        @NotNull @Positive BigInteger amount,
        @Size(max = 128) String narration,
        @NotBlank @Pattern(regexp = "\\d{4}", message = "transaction PIN must be exactly 4 digits") String transactionPin
) {
}
