package ng.com.nawill.pay.payments.transaction;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.BadRequestException;

/**
 * The filter set shared by both the paginated transaction list and the
 * analytics endpoint, so the two never drift on what "matching transactions"
 * means. {@code term} is matched against {@link Transaction#getSessionId()} -
 * the only caller-facing free-text reference on a transaction.
 */
public record TransactionFilter(
        Instant startDate,
        Instant endDate,
        TransactionStatus status,
        TransactionType type,
        String term,
        UUID virtualAccountId,
        BigInteger minAmount,
        BigInteger maxAmount
) {

    public TransactionFilter {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BadRequestException("INVALID_DATE_RANGE", "startDate must not be after endDate");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new BadRequestException("INVALID_AMOUNT_RANGE", "minAmount must not be greater than maxAmount");
        }
    }
}
