package ng.com.nawill.pay.payments.collectionaccount;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** No accountName: it is always resolved via Name Enquiry, never client-supplied (see BankAccountService). */
public record CreateCollectionAccountRequest(
        @NotNull UUID bankId,
        @NotBlank String accountNumber
) {
}
