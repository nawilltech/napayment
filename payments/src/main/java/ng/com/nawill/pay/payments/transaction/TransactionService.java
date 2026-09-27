package ng.com.nawill.pay.payments.transaction;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.common.logging.PiiMasker;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.payments.collectionaccount.CollectionAccount;
import ng.com.nawill.pay.payments.collectionaccount.CollectionAccountRepository;
import ng.com.nawill.pay.payments.processor.PaymentProcessor;
import ng.com.nawill.pay.payments.processor.PaymentProcessorGateway;
import ng.com.nawill.pay.payments.processor.PaymentProcessorRepository;
import ng.com.nawill.pay.payments.settlement.SettlementService;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccount;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final PaymentProcessorRepository paymentProcessorRepository;
    private final PaymentProcessorGateway paymentProcessorGateway;
    private final CollectionAccountRepository collectionAccountRepository;
    private final SettlementService settlementService;
    private final CurrentUserResolver currentUserResolver;

    public TransactionService(TransactionRepository transactionRepository,
                               VirtualAccountRepository virtualAccountRepository,
                               PaymentProcessorRepository paymentProcessorRepository,
                               PaymentProcessorGateway paymentProcessorGateway,
                               CollectionAccountRepository collectionAccountRepository,
                               SettlementService settlementService,
                               CurrentUserResolver currentUserResolver) {
        this.transactionRepository = transactionRepository;
        this.virtualAccountRepository = virtualAccountRepository;
        this.paymentProcessorRepository = paymentProcessorRepository;
        this.paymentProcessorGateway = paymentProcessorGateway;
        this.collectionAccountRepository = collectionAccountRepository;
        this.settlementService = settlementService;
        this.currentUserResolver = currentUserResolver;
    }

    public Transaction create(CreateTransactionRequest request, String idempotencyKey) {
        VirtualAccount virtualAccount = lockVirtualAccount(request.virtualAccountId());
        assertOwnership(virtualAccount);
        return process(request, idempotencyKey, virtualAccount);
    }

    /**
     * For system-initiated credits where there is no authenticated caller to
     * assert ownership against - payment-link redemption and dynamic-account
     * deposit simulation are both paid by an anonymous third party, not a
     * logged-in Nawill Pay user. The virtual account id is the trust
     * boundary here instead: it was already resolved and validated by the
     * caller (against the specific payment link / dynamic account it
     * belongs to) before this is invoked. Shares every other step - ledger
     * effect, locking, collection-account mirroring, auto-settle - with
     * {@link #create}, so there is exactly one place that logic lives.
     */
    public Transaction createForPaymentIntent(CreateTransactionRequest request, String idempotencyKey) {
        VirtualAccount virtualAccount = lockVirtualAccount(request.virtualAccountId());
        return process(request, idempotencyKey, virtualAccount);
    }

    @Transactional(readOnly = true)
    public Transaction get(UUID id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND));
        assertOwnership(transaction.getVirtualAccount());
        return transaction;
    }

    /**
     * Backs both the list and analytics endpoints - row-level ownership
     * scoping and every optional filter live in {@link TransactionSpecifications}
     * so the two can never see a different notion of "matching transactions".
     */
    @Transactional(readOnly = true)
    public Page<Transaction> list(TransactionFilter filter, Pageable pageable) {
        CurrentUser currentUser = currentUserResolver.requireCurrentUser();
        return transactionRepository.findAll(TransactionSpecifications.build(filter, currentUser), pageable);
    }

    @Transactional(readOnly = true)
    public TransactionAnalyticsResponse analyze(TransactionFilter filter) {
        CurrentUser currentUser = currentUserResolver.requireCurrentUser();
        List<Transaction> transactions =
                transactionRepository.findAll(TransactionSpecifications.build(filter, currentUser));
        return TransactionAnalyticsResponse.from(filter.startDate(), filter.endDate(), transactions);
    }

    private VirtualAccount lockVirtualAccount(UUID virtualAccountId) {
        // Locked immediately (not just around applyLedgerEffect) - the sandboxed
        // gateway call below is synchronous/instant today; if real processor I/O
        // ever replaces it (v0.2), re-scope this lock to just the ledger mutation
        // so the row isn't held across a network call.
        return virtualAccountRepository.findByIdForUpdate(virtualAccountId)
                .orElseThrow(() -> new ApiException(ErrorCode.VIRTUAL_ACCOUNT_NOT_FOUND));
    }

    private Transaction process(CreateTransactionRequest request, String idempotencyKey, VirtualAccount virtualAccount) {
        PaymentProcessor processor = paymentProcessorRepository.findById(request.paymentProcessorId())
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_PROCESSOR_NOT_FOUND));

        Transaction transaction = new Transaction(request.amount(), idempotencyKey, processor,
                request.transactionType(), UUID.randomUUID().toString(), virtualAccount);
        // TODO(FR-11): compute charge from configurable amount-tiers instead of zero.
        transaction = transactionRepository.save(transaction);
        logTransition(transaction, "created");

        transaction.transitionTo(TransactionStatus.PROCESSING);
        logTransition(transaction, "processing started");

        PaymentProcessorGateway.ProcessorChargeResult result =
                paymentProcessorGateway.charge(request.amount(), virtualAccount.getCurrency(), "Nawill Pay collection");

        if (result.successful()) {
            transaction.transitionTo(TransactionStatus.PAID);
            applyLedgerEffect(virtualAccount, transaction);
            logTransition(transaction, "processor confirmed, ledger updated");
        } else {
            transaction.transitionTo(TransactionStatus.FAILED);
            logTransition(transaction, "processor declined");
        }

        return transaction;
    }

    private void applyLedgerEffect(VirtualAccount virtualAccount, Transaction transaction) {
        BigInteger amount = transaction.getAmount();
        if (transaction.getTransactionType() == TransactionType.CREDIT) {
            virtualAccount.credit(amount);
            mirrorOnCollectionAccount(collectionAccount -> collectionAccount.credit(amount));
            settlementService.autoSettleIfEnabled(virtualAccount);
        } else {
            virtualAccount.debit(amount);
            mirrorOnCollectionAccount(collectionAccount -> collectionAccount.debit(amount));
        }
    }

    /**
     * Best-effort ledger mirror onto the pooled collection account (doc 3
     * §3's reconciliation invariant, ADR-5 doc 2 §7) - always locked AFTER
     * VirtualAccount (already locked by the caller), never the reverse, so
     * lock ordering stays consistent with {@code SettlementService}. Skips
     * silently (with a warning) if no collection account has been
     * configured yet - this mirror is a reconciliation aid, not a
     * prerequisite for the core collection/debit flow to work.
     */
    private void mirrorOnCollectionAccount(Consumer<CollectionAccount> mutation) {
        collectionAccountRepository.findActiveForUpdate().ifPresentOrElse(mutation,
                () -> log.warn("no active collection account configured, skipping ledger mirror"));
    }

    private void assertOwnership(VirtualAccount virtualAccount) {
        CurrentUser currentUser = currentUserResolver.requireCurrentUser();
        if (currentUser.isSuperAdmin()) {
            return;
        }
        boolean owned = virtualAccount.isOwnedByUser(currentUser.userId())
                || (currentUser.hasBusinessScope() && virtualAccount.isOwnedByBusiness(currentUser.businessId()));
        if (!owned) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }

    private void logTransition(Transaction transaction, String note) {
        log.info("transaction state transition: transactionId={} status={} idempotencyKey={} note={}",
                transaction.getId(), transaction.getTransactionStatus(),
                PiiMasker.maskKeepLast4(transaction.getIdempotencyKey()), note);
    }
}
