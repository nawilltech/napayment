package ng.com.nawill.pay.payments.paymentmethod;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Only the fields present change; an empty description clears it. The code can't change. */
public record UpdatePaymentMethodRequest(
        @Size(max = 64) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Size(max = 256) String description,
        @Min(0) @Max(1000) Integer displayOrder
) {
}
