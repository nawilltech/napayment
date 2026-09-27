package ng.com.nawill.pay.payments.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigInteger;
import java.util.Locale;
import java.util.UUID;
import ng.com.nawill.pay.payments.paymentmethod.PaymentMethod;

/**
 * {@code paymentProcessorId} is optional: omitted, the processor is routed
 * (FR-Proc-4); given, it must be available to the account. A missing
 * {@code paymentMethod} means TRANSFER.
 */
public record CreateTransactionRequest(
        @NotNull UUID virtualAccountId,
        UUID paymentProcessorId,
        /** A payment_methods code (e.g. CARD). */
        @Size(max = 32) String paymentMethod,
        @NotNull TransactionType transactionType,
        @NotNull @Positive BigInteger amount
) {

    /** A collection routed by method - what collect, payment links and one-time accounts create. */
    public static CreateTransactionRequest routedCredit(UUID virtualAccountId, String paymentMethod,
                                                        BigInteger amount) {
        return new CreateTransactionRequest(virtualAccountId, null, paymentMethod, TransactionType.CREDIT, amount);
    }

    public String paymentMethodOrDefault() {
        return paymentMethod == null || paymentMethod.isBlank()
                ? PaymentMethod.DEFAULT_CODE
                : paymentMethod.trim().toUpperCase(Locale.ROOT);
    }
}
