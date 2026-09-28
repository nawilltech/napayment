package ng.com.nawill.pay.payments.paymentmethod;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code code} is stored upper-case and never changes; displayOrder defaults to 100 (lower shows first). */
public record CreatePaymentMethodRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,31}$",
                message = "must be 2-32 letters, digits or underscores, starting with a letter") String code,
        @NotBlank @Size(max = 64) String name,
        @Size(max = 256) String description,
        @Min(0) @Max(1000) Integer displayOrder
) {
}
