package ng.com.nawill.pay.payments.bankaccount;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateBankAccountRequest(
        @NotNull UUID bankId,
        @NotBlank String accountNumber
) {
}
