package ng.com.nawill.pay.payments.transaction;

import java.math.BigInteger;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.ResourceNotFoundException;
import ng.com.nawill.pay.common.logging.PiiMasker;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.payments.processor.PaymentProcessor;
import ng.com.nawill.pay.payments.processor.PaymentProcessorGateway;
import ng.com.nawill.pay.payments.processor.PaymentProcessorRepository;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccount;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
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
    private final CurrentUserResolver currentUserResolver;

    public TransactionService(TransactionRepository transactionRepository,
                               VirtualAccountRepository virtualAccountRepository,
                               PaymentProcessorRepository paymentProcessorRepository,
                               PaymentProcessorGateway paymentProcessorGateway,
                               CurrentUserResolver currentUserResolver) {
        this.transactionRepository = transactionRepository;
        this.virtualAccountRepository = virtualAccountRepository;
        this.paymentProcessorRepository = paymentProcessorRepository;
        this.paymentProcessorGateway = paymentProcessorGateway;
        this.currentUserResolver = currentUserResolver;
    }

    public Transaction create(CreateTransactionRequest request, String idempotencyKey) {
        VirtualAccount virtualAccount = virtualAccountRepository.findById(request.virtualAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Virtual account not found: " + request.virtualAccountId()));
        assertOwnership(virtualAccount);

        PaymentProcessor processor = paymentProcessorRepository.findById(request.paymentProcessorId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment processor not found: " + request.paymentProcessorId()));

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

    @Transactional(readOnly = true)
    public Transaction get(UUID id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
        assertOwnership(transaction.getVirtualAccount());
        return transaction;
    }

    private void applyLedgerEffect(VirtualAccount virtualAccount, Transaction transaction) {
        if (transaction.getTransactionType() == TransactionType.CREDIT) {
            virtualAccount.credit(transaction.getAmount());
        } else {
            virtualAccount.debit(transaction.getAmount());
        }
    }

    private void assertOwnership(VirtualAccount virtualAccount) {
        CurrentUser currentUser = currentUserResolver.requireCurrentUser();
        if (currentUser.isSuperAdmin()) {
            return;
        }
        boolean owned = virtualAccount.isOwnedByUser(currentUser.userId())
                || (currentUser.hasBusinessScope() && virtualAccount.isOwnedByBusiness(currentUser.businessId()));
        if (!owned) {
            throw new AccessDeniedException("Virtual account does not belong to the caller");
        }
    }

    private void logTransition(Transaction transaction, String note) {
        log.info("transaction state transition: transactionId={} status={} idempotencyKey={} note={}",
                transaction.getId(), transaction.getTransactionStatus(),
                PiiMasker.maskKeepLast4(transaction.getIdempotencyKey()), note);
    }
}
