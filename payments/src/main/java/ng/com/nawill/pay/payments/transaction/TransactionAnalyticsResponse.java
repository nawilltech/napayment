package ng.com.nawill.pay.payments.transaction;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregated view over whatever {@link TransactionFilter} the caller passed
 * (same filters, and the same ownership scoping, as the list endpoint - see
 * {@link TransactionSpecifications}). Computed in application code rather
 * than pushed down as SQL aggregates: at current transaction volumes a full
 * scan of the filtered set is cheap, and it keeps this endpoint free of
 * Postgres-specific grouping functions. Revisit (DB-side GROUP BY) if/when
 * per-business transaction counts grow large enough for that to matter.
 */
public record TransactionAnalyticsResponse(
        Instant fromDate,
        Instant toDate,
        long totalCount,
        BigInteger totalVolume,
        BigInteger creditVolume,
        BigInteger debitVolume,
        BigInteger netVolume,
        BigInteger averageAmount,
        TransactionSummary highest,
        TransactionSummary lowest,
        List<StatusBreakdown> byStatus,
        List<TypeBreakdown> byType,
        List<DailyVolume> dailyVolume
) {

    public record TransactionSummary(UUID transactionId, BigInteger amount, Instant createdAt) {
    }

    public record StatusBreakdown(TransactionStatus status, long count, BigInteger volume) {
    }

    public record TypeBreakdown(TransactionType type, long count, BigInteger volume) {
    }

    public record DailyVolume(LocalDate date, long count, BigInteger volume) {
    }

    public static TransactionAnalyticsResponse from(Instant fromDate, Instant toDate, List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return new TransactionAnalyticsResponse(fromDate, toDate, 0, BigInteger.ZERO, BigInteger.ZERO,
                    BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, null, null, List.of(), List.of(), List.of());
        }

        BigInteger totalVolume = sumAmounts(transactions);
        BigInteger creditVolume = sumAmounts(byType(transactions, TransactionType.CREDIT));
        BigInteger debitVolume = sumAmounts(byType(transactions, TransactionType.DEBIT));
        BigInteger averageAmount = totalVolume.divide(BigInteger.valueOf(transactions.size()));

        Transaction highest = transactions.stream().max(Comparator.comparing(Transaction::getAmount)).orElseThrow();
        Transaction lowest = transactions.stream().min(Comparator.comparing(Transaction::getAmount)).orElseThrow();

        List<StatusBreakdown> byStatus = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getTransactionStatus))
                .entrySet().stream()
                .map(entry -> new StatusBreakdown(entry.getKey(), entry.getValue().size(), sumAmounts(entry.getValue())))
                .sorted(Comparator.comparing(StatusBreakdown::status))
                .toList();

        List<TypeBreakdown> byType = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getTransactionType))
                .entrySet().stream()
                .map(entry -> new TypeBreakdown(entry.getKey(), entry.getValue().size(), sumAmounts(entry.getValue())))
                .sorted(Comparator.comparing(TypeBreakdown::type))
                .toList();

        Map<LocalDate, List<Transaction>> byDay = new TreeMap<>(transactions.stream()
                .collect(Collectors.groupingBy(t -> t.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate())));
        List<DailyVolume> dailyVolume = byDay.entrySet().stream()
                .map(entry -> new DailyVolume(entry.getKey(), entry.getValue().size(), sumAmounts(entry.getValue())))
                .toList();

        return new TransactionAnalyticsResponse(fromDate, toDate, transactions.size(), totalVolume, creditVolume,
                debitVolume, creditVolume.subtract(debitVolume), averageAmount,
                new TransactionSummary(highest.getId(), highest.getAmount(), highest.getCreatedAt()),
                new TransactionSummary(lowest.getId(), lowest.getAmount(), lowest.getCreatedAt()),
                byStatus, byType, dailyVolume);
    }

    private static List<Transaction> byType(List<Transaction> transactions, TransactionType type) {
        return transactions.stream().filter(t -> t.getTransactionType() == type).toList();
    }

    private static BigInteger sumAmounts(List<Transaction> transactions) {
        return transactions.stream().map(Transaction::getAmount).reduce(BigInteger.ZERO, BigInteger::add);
    }
}
