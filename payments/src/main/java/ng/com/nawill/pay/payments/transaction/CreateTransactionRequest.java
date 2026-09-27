package ng.com.nawill.pay.payments.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigInteger;
import java.util.UUID;
import ng.com.nawill.pay.payments.processor.PaymentMethod;

/**
 * {@code paymentProcessorId} is optional: omitted, the processor is routed
 * (FR-Proc-4); given, it must be available to the account. A missing
 * {@code paymentMethod} means TRANSFER.
 */
public record CreateTransactionRequest(
        @NotNull UUID virtualAccountId,
        UUID paymentProcessorId,
        PaymentMethod paymentMethod,
        @NotNull TransactionType transactionType,
        @NotNull @Positive BigInteger amount
) {

    /** A collection routed by method - what collect, payment links and one-time accounts create. */
    public static CreateTransactionRequest routedCredit(UUID virtualAccountId, PaymentMethod paymentMethod,
                                                        BigInteger amount) {
        return new CreateTransactionRequest(virtualAccountId, null, paymentMethod, TransactionType.CREDIT, amount);
    }

    public PaymentMethod paymentMethodOrDefault() {
        return paymentMethod == null ? PaymentMethod.TRANSFER : paymentMethod;
    }
}
