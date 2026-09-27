package ng.com.nawill.pay.payments.settlement;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.payments.collectionaccount.CollectionAccount;
import ng.com.nawill.pay.payments.collectionaccount.CollectionAccountRepository;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccount;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Splits a virtual account's balance across its configured settlement
 * accounts and disburses each share (doc 4 §C.8, FR-Settle-1). If configured
 * split percentages sum to less than 100, only that proportional amount
 * moves - the remainder stays in the virtual account, it is not swept out.
 * The last split absorbs any rounding remainder so the parts always sum
 * exactly to the settled sub-total (ADR-7, doc 2 §7).
 * <p>
 * {@code REQUIRES_NEW}: when called from {@link #autoSettleIfEnabled}
 * inside the same transaction as the originating credit, a failure here
 * must roll back only the settlement, never the credit that triggered it.
 * Without a fresh transaction, an uncaught exception here would mark the
 * *shared* transaction rollback-only at the proxy boundary even if the
 * caller catches it - silently rolling back the credit too.
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final SettlementRepository settlementRepository;
    private final SettlementAccountRepository settlementAccountRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final CollectionAccountRepository collectionAccountRepository;
    private final SettlementGateway settlementGateway;

    public SettlementService(SettlementRepository settlementRepository,
                              SettlementAccountRepository settlementAccountRepository,
                              VirtualAccountRepository virtualAccountRepository,
                              CollectionAccountRepository collectionAccountRepository,
                              SettlementGateway settlementGateway) {
        this.settlementRepository = settlementRepository;
        this.settlementAccountRepository = settlementAccountRepository;
        this.virtualAccountRepository = virtualAccountRepository;
        this.collectionAccountRepository = collectionAccountRepository;
        this.settlementGateway = settlementGateway;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Settlement> settle(UUID virtualAccountId, BigInteger requestedAmount, String idempotencyKey) {
        // Always lock VirtualAccount before CollectionAccount - the fixed
        // ordering every balance-mutating path in this codebase follows,
        // so concurrent settlements can never deadlock against each other.
        VirtualAccount virtualAccount = virtualAccountRepository.findByIdForUpdate(virtualAccountId)
                .orElseThrow(() -> new ApiException(ErrorCode.VIRTUAL_ACCOUNT_NOT_FOUND));

        List<SettlementAccount> settlementAccounts = settlementAccountRepository.findByVirtualAccountId(virtualAccountId);
        if (settlementAccounts.isEmpty()) {
            throw new ApiException(ErrorCode.NO_SETTLEMENT_ACCOUNTS);
        }

        BigInteger amount = requestedAmount != null ? requestedAmount : virtualAccount.getBalance();
        if (amount.compareTo(BigInteger.ZERO) <= 0) {
            throw new ApiException(ErrorCode.NOTHING_TO_SETTLE);
        }
        if (amount.compareTo(virtualAccount.getBalance()) > 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        BigDecimal totalPercentage = settlementAccounts.stream()
                .map(SettlementAccount::getSplitPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigInteger amountToSettle = new BigDecimal(amount)
                .multiply(totalPercentage)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .toBigInteger();
        if (amountToSettle.compareTo(BigInteger.ZERO) <= 0) {
            throw new ApiException(ErrorCode.NOTHING_TO_SETTLE);
        }

        CollectionAccount collectionAccount = collectionAccountRepository.findActiveForUpdate()
                .orElseThrow(() -> new ApiException(ErrorCode.COLLECTION_ACCOUNT_NOT_FOUND));

        List<Settlement> results = disburse(virtualAccount, settlementAccounts, amountToSettle, totalPercentage, idempotencyKey);

        virtualAccount.debit(amountToSettle);
        collectionAccount.debit(amountToSettle);

        log.info("settlement completed: virtualAccountId={} amountToSettle={} splits={}",
                virtualAccountId, amountToSettle, results.size());
        return results;
    }

    /** Failures are logged, never propagated - a broken auto-settle must never fail the collection that triggered it. */
    public void autoSettleIfEnabled(VirtualAccount virtualAccount) {
        if (!virtualAccount.isAutoSettle()) {
            return;
        }
        try {
            settle(virtualAccount.getId(), null, "auto-settle:" + virtualAccount.getId() + ":" + UUID.randomUUID());
        } catch (Exception e) {
            log.warn("auto-settle failed, collection is unaffected: virtualAccountId={} error={}",
                    virtualAccount.getId(), e.getMessage());
        }
    }

    private List<Settlement> disburse(VirtualAccount virtualAccount, List<SettlementAccount> settlementAccounts,
                                       BigInteger amountToSettle, BigDecimal totalPercentage, String idempotencyKey) {
        List<Settlement> results = new ArrayList<>();
        BigInteger allocated = BigInteger.ZERO;

        for (int i = 0; i < settlementAccounts.size(); i++) {
            SettlementAccount settlementAccount = settlementAccounts.get(i);
            boolean isLast = i == settlementAccounts.size() - 1;
            BigInteger splitAmount = isLast
                    ? amountToSettle.subtract(allocated)
                    : new BigDecimal(amountToSettle)
                            .multiply(settlementAccount.getSplitPercentage())
                            .divide(totalPercentage, 0, RoundingMode.DOWN)
                            .toBigInteger();
            allocated = allocated.add(splitAmount);

            Settlement settlement = new Settlement(virtualAccount, settlementAccount, splitAmount,
                    idempotencyKey + ":" + settlementAccount.getId());
            settlement = settlementRepository.save(settlement);
            settlement.transitionTo(SettlementStatus.PROCESSING);

            SettlementGateway.DisbursementResult result =
                    settlementGateway.disburse(splitAmount, settlementAccount.getBankAccount());
            if (result.successful()) {
                settlement.transitionTo(SettlementStatus.COMPLETED);
                settlement.assignReference(result.reference());
            } else {
                settlement.transitionTo(SettlementStatus.FAILED);
            }
            results.add(settlement);
        }
        return results;
    }
}
