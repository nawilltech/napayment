package ng.com.nawill.pay.payments.settlement;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateSettlementAccountRequest(
        @NotNull UUID bankAccountId,
        @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal splitPercentage
) {
}
