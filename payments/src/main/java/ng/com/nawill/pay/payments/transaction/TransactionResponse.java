package ng.com.nawill.pay.payments.transaction;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.payments.processor.PaymentMethod;

public record TransactionResponse(
        UUID id,
        BigInteger amount,
        BigInteger charge,
        TransactionStatus transactionStatus,
        TransactionType transactionType,
        String sessionId,
        UUID virtualAccountId,
        UUID paymentProcessorId,
        PaymentMethod paymentMethod,
        UUID transferGroupId,
        UUID counterpartyAccountId,
        Instant createdAt
) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getCharge(),
                transaction.getTransactionStatus(),
                transaction.getTransactionType(),
                transaction.getSessionId(),
                transaction.getVirtualAccount().getId(),
                transaction.getPaymentProcessor() == null ? null : transaction.getPaymentProcessor().getId(),
                transaction.getPaymentMethod(),
                transaction.getTransferGroupId(),
                transaction.getCounterpartyAccountId(),
                transaction.getCreatedAt());
    }
}
