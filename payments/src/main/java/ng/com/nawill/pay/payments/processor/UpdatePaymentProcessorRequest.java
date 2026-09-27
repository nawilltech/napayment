package ng.com.nawill.pay.payments.processor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Only the fields present are changed. */
public record UpdatePaymentProcessorRequest(
        @Size(max = 64) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Min(0) @Max(1000) Integer priority
) {
}
