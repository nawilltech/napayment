package ng.com.nawill.pay.onboarding.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * {@code currentPin} is required only when the caller already has a PIN set
 * (changing one) - the first-time set has nothing to verify it against,
 * validated in {@link TransactionPinService}, not here (bean validation
 * can't see the caller's existing state).
 */
public record SetTransactionPinRequest(
        @NotBlank String currentPassword,
        String currentPin,
        @NotBlank @Pattern(regexp = "\\d{4}", message = "PIN must be exactly 4 digits") String pin,
        @NotBlank String confirmPin
) {
}
