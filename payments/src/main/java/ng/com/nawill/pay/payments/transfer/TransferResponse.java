package ng.com.nawill.pay.payments.transfer;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.payments.transaction.Transaction;

public record TransferResponse(
        UUID id,
        UUID transferGroupId,
        BigInteger amount,
        String recipientDisplayName,
        BigInteger senderNewBalance,
        Instant createdAt
) {

    static TransferResponse from(Transaction debitTransaction, String recipientDisplayName, BigInteger senderNewBalance) {
        return new TransferResponse(debitTransaction.getId(), debitTransaction.getTransferGroupId(),
                debitTransaction.getAmount(), recipientDisplayName, senderNewBalance, debitTransaction.getCreatedAt());
    }
}
