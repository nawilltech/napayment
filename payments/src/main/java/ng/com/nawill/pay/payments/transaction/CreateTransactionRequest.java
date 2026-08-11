package ng.com.nawill.pay.payments.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigInteger;
import java.util.UUID;

public record CreateTransactionRequest(
        @NotNull UUID virtualAccountId,
        @NotNull UUID paymentProcessorId,
        @NotNull TransactionType transactionType,
        @NotNull @Positive BigInteger amount
) {
}
