package ng.com.nawill.pay.payments.collectionaccount;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateCollectionAccountRequest(
        @NotNull UUID bankId,
        @NotBlank String accountNumber,
        @NotBlank String accountName
) {
}
