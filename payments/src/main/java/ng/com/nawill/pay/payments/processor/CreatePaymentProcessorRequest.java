package ng.com.nawill.pay.payments.processor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * {@code code} identifies the integration (e.g. PAYSTACK) and its
 * environment configuration; stored upper-case and never changed after
 * creation. Priority defaults to 100 and defaultEnabled to true; logo is
 * optional.
 */
public record CreatePaymentProcessorRequest(
        @NotBlank @Size(max = 64) String name,
        @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,31}$",
                message = "must be 2-32 letters, digits or underscores, starting with a letter") String code,
        @Min(0) @Max(1000) Integer priority,
        Boolean defaultEnabled,
        /** payment_methods codes, e.g. TRANSFER, CARD. */
        @NotEmpty Set<String> methods,
        /** Optional base64 data URL - see {@link ProcessorLogo}. */
        String logo
) {
}
