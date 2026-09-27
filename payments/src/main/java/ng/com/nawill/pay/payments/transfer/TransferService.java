package ng.com.nawill.pay.payments.transfer;

import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.common.logging.PiiMasker;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.payments.transaction.Transaction;
import ng.com.nawill.pay.payments.transaction.TransactionRepository;
import ng.com.nawill.pay.payments.transaction.TransactionStatus;
import ng.com.nawill.pay.payments.transaction.TransactionType;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccount;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccountQueryService;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-Auth-1: wallet-to-wallet transfer between two Nawill virtual accounts.
 * Deliberately separate from {@code TransactionService.process()} rather than
 * reusing it - a transfer never calls {@code PaymentProcessorGateway} (there
 * is no external processor involved) and, critically, never touches
 * {@code CollectionAccount}: doc 2 §7 ADR-5's reconciliation invariant exists
 * because the collection account mirrors real, external bank movement, and a
 * transfer between two internal virtual accounts changes which account holds
 * the value without moving a single kobo in or out of the real pooled bank
 * account. Reusing {@code process()} here would silently corrupt that
 * invariant.
 */
@Service
@Transactional
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final int ACCOUNT_NUMBER_LENGTH = 10;

    private final VirtualAccountRepository virtualAccountRepository;
    private final VirtualAccountQueryService virtualAccountQueryService;
    private final TransactionRepository transactionRepository;
    private final CurrentUserResolver currentUserResolver;
    private final RecipientDirectory recipientDirectory;
    private final TransactionPinGateway transactionPinGateway;

    public TransferService(VirtualAccountRepository virtualAccountRepository,
                            VirtualAccountQueryService virtualAccountQueryService,
                            TransactionRepository transactionRepository,
                            CurrentUserResolver currentUserResolver,
                            RecipientDirectory recipientDirectory,
                            TransactionPinGateway transactionPinGateway) {
        this.virtualAccountRepository = virtualAccountRepository;
        this.virtualAccountQueryService = virtualAccountQueryService;
        this.transactionRepository = transactionRepository;
        this.currentUserResolver = currentUserResolver;
        this.recipientDirectory = recipientDirectory;
        this.transactionPinGateway = transactionPinGateway;
    }

    @Transactional(readOnly = true)
    public TransferResolveResponse resolve(String identifier) {
        VirtualAccount recipient = resolveRecipientAccount(identifier)
                .orElseThrow(() -> new ApiException(ErrorCode.RECIPIENT_NOT_FOUND, identifier));
        return new TransferResolveResponse(displayNameFor(recipient), PiiMasker.maskKeepLast4(recipient.getAccountNumber()));
    }

    public TransferResponse transfer(TransferRequest request, String idempotencyKey) {
        CurrentUser currentUser = currentUserResolver.requireCurrentUser();
        VirtualAccount callerAccount = virtualAccountQueryService.requireSoleVirtualAccountForCaller();

        VirtualAccount recipientAccount = resolveRecipientAccount(request.recipientIdentifier())
                .orElseThrow(() -> new ApiException(ErrorCode.RECIPIENT_NOT_FOUND, request.recipientIdentifier()));
        if (recipientAccount.getId().equals(callerAccount.getId())) {
            throw new ApiException(ErrorCode.SELF_TRANSFER);
        }

        // Checked before any row is locked - a wrong PIN should fail fast
        // without ever touching the ledger.
        transactionPinGateway.verify(currentUser.userId(), request.transactionPin());

        // Fixed lock order independent of sender/recipient direction (doc 2
        // §7 ADR-9's rule extended to two VirtualAccount rows): otherwise a
        // concurrent transfer running the opposite direction between the
        // same two accounts could deadlock.
        boolean callerFirst = callerAccount.getId().compareTo(recipientAccount.getId()) < 0;
        UUID firstId = callerFirst ? callerAccount.getId() : recipientAccount.getId();
        UUID secondId = callerFirst ? recipientAccount.getId() : callerAccount.getId();
        VirtualAccount first = lockAccount(firstId);
        VirtualAccount second = lockAccount(secondId);
        VirtualAccount sender = callerFirst ? first : second;
        VirtualAccount recipient = callerFirst ? second : first;

        if (!sender.getCurrency().equals(recipient.getCurrency())) {
            throw new ApiException(ErrorCode.CURRENCY_MISMATCH);
        }
        if (sender.getBalance().compareTo(request.amount()) < 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        UUID transferGroupId = UUID.randomUUID();
        String sessionId = UUID.randomUUID().toString();

        Transaction debit = new Transaction(request.amount(), idempotencyKey + ":debit", TransactionType.DEBIT,
                sessionId, sender, transferGroupId, recipient.getId());
        Transaction credit = new Transaction(request.amount(), idempotencyKey + ":credit", TransactionType.CREDIT,
                sessionId, recipient, transferGroupId, sender.getId());

        sender.debit(request.amount());
        recipient.credit(request.amount());

        // Synchronous, internal-only ledger move - no external gateway call
        // to wait on, so there's no PROCESSING interval worth recording.
        debit.transitionTo(TransactionStatus.PAID);
        credit.transitionTo(TransactionStatus.PAID);

        debit = transactionRepository.save(debit);
        transactionRepository.save(credit);

        log.info("transfer completed: transferGroupId={} senderAccountId={} recipientAccountId={} amount={}",
                transferGroupId, sender.getId(), recipient.getId(), request.amount());

        return TransferResponse.from(debit, displayNameFor(recipient), sender.getBalance());
    }

    private VirtualAccount lockAccount(UUID virtualAccountId) {
        return virtualAccountRepository.findByIdForUpdate(virtualAccountId)
                .orElseThrow(() -> new ApiException(ErrorCode.VIRTUAL_ACCOUNT_NOT_FOUND));
    }

    private Optional<VirtualAccount> resolveRecipientAccount(String identifier) {
        if (identifier.matches("\\d{" + ACCOUNT_NUMBER_LENGTH + "}")) {
            return virtualAccountRepository.findByAccountNumber(identifier);
        }
        return recipientDirectory.resolveUserIdByPhone(identifier)
                .flatMap(userId -> virtualAccountRepository.findByUserId(userId).stream().findFirst());
    }

    private String displayNameFor(VirtualAccount account) {
        if (account.getUserId() == null) {
            return "a Nawill business account";
        }
        return recipientDirectory.nameFor(account.getUserId())
                .map(this::maskName)
                .orElse("a Nawill user");
    }

    private String maskName(RecipientDirectory.RecipientName name) {
        String first = name.firstName();
        String maskedFirst = first == null || first.length() <= 2 ? "***" : first.substring(0, 2) + "***";
        String lastInitial = name.lastName() == null || name.lastName().isBlank()
                ? "" : " " + name.lastName().charAt(0) + ".";
        return maskedFirst + lastInitial;
    }
}
