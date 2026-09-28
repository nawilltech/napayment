package ng.com.nawill.pay.payments.dynamicaccount;

import java.time.Duration;
import java.time.Instant;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.payments.paymentmethod.PaymentMethod;
import ng.com.nawill.pay.payments.transaction.CreateTransactionRequest;
import ng.com.nawill.pay.payments.transaction.Transaction;
import ng.com.nawill.pay.payments.transaction.TransactionService;
import ng.com.nawill.pay.payments.transaction.TransactionStatus;
import ng.com.nawill.pay.payments.util.AccountNumberGenerator;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccount;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccountQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dynamic/temporary virtual accounts (FR-DynAcct-1, doc 4 §C.10). Expiry is
 * checked lazily at lookup/deposit time rather than via a scheduled sweep -
 * nothing needs to *react* to expiry happening, only refuse to honor it
 * afterward, and this codebase has no scheduled components yet (ADR-11,
 * doc 2 §7).
 */
@Service
@Transactional
public class DynamicVirtualAccountService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final DynamicVirtualAccountRepository dynamicVirtualAccountRepository;
    private final VirtualAccountQueryService virtualAccountQueryService;
    private final AccountNumberGenerator accountNumberGenerator;
    private final TransactionService transactionService;
    private final CurrentUserResolver currentUserResolver;

    public DynamicVirtualAccountService(DynamicVirtualAccountRepository dynamicVirtualAccountRepository,
                                         VirtualAccountQueryService virtualAccountQueryService,
                                         AccountNumberGenerator accountNumberGenerator,
                                         TransactionService transactionService,
                                         CurrentUserResolver currentUserResolver) {
        this.dynamicVirtualAccountRepository = dynamicVirtualAccountRepository;
        this.virtualAccountQueryService = virtualAccountQueryService;
        this.accountNumberGenerator = accountNumberGenerator;
        this.transactionService = transactionService;
        this.currentUserResolver = currentUserResolver;
    }

    public DynamicVirtualAccount mint(CreateDynamicAccountRequest request) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        VirtualAccount virtualAccount = virtualAccountQueryService.requireSoleVirtualAccountForCaller();

        String accountNumber = accountNumberGenerator.generateUnique(dynamicVirtualAccountRepository::existsByAccountNumber);
        Instant expiresAt = request.expiresAt() != null ? request.expiresAt() : Instant.now().plus(DEFAULT_TTL);

        DynamicVirtualAccount account = new DynamicVirtualAccount(currentUser.businessId(), virtualAccount,
                accountNumber, request.expectedAmount(), expiresAt, request.reference());
        return dynamicVirtualAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Page<DynamicVirtualAccount> listForCallerBusiness(Pageable pageable) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        return dynamicVirtualAccountRepository.findByBusinessId(currentUser.businessId(), pageable);
    }

    /** Locked for the whole eligibility-check -> transact -> mark-paid sequence, same concurrency principle as payment-link redemption. */
    public Transaction simulateDeposit(String accountNumber, SimulateDepositRequest request, String idempotencyKey) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        DynamicVirtualAccount account = dynamicVirtualAccountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new ApiException(ErrorCode.DYNAMIC_ACCOUNT_NOT_FOUND));
        if (!account.isOwnedByBusiness(currentUser.businessId())) {
            throw new ApiException(ErrorCode.DYNAMIC_ACCOUNT_NOT_FOUND);
        }

        if (account.isExpired() && account.getDynamicAccountStatus() == DynamicAccountStatus.ACTIVE) {
            account.markExpired();
        }
        if (!account.isDepositable()) {
            throw new ApiException(ErrorCode.DYNAMIC_ACCOUNT_NOT_DEPOSITABLE, account.getDynamicAccountStatus().name().toLowerCase());
        }

        // A one-time account is paid into by bank transfer.
        CreateTransactionRequest createRequest = CreateTransactionRequest.routedCredit(
                account.getVirtualAccount().getId(), PaymentMethod.DEFAULT_CODE, request.amount());
        Transaction transaction = transactionService.create(createRequest, idempotencyKey);

        if (transaction.getTransactionStatus() == TransactionStatus.PAID) {
            account.markPaid();
        }
        return transaction;
    }
}
