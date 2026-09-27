package ng.com.nawill.pay.payments.bankaccount;

import java.util.UUID;
import ng.com.nawill.pay.common.entity.EntityStatus;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.payments.bankverification.BankVerificationGateway.ResolvedAccount;
import ng.com.nawill.pay.payments.bankverification.BankVerificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final BankVerificationService bankVerificationService;
    private final CurrentUserResolver currentUserResolver;

    public BankAccountService(BankAccountRepository bankAccountRepository,
                               BankVerificationService bankVerificationService,
                               CurrentUserResolver currentUserResolver) {
        this.bankAccountRepository = bankAccountRepository;
        this.bankVerificationService = bankVerificationService;
        this.currentUserResolver = currentUserResolver;
    }

    public BankAccount create(CreateBankAccountRequest request) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        return createForBusiness(currentUser.businessId(), request);
    }

    /** Platform admin registering a settlement bank account on a business's behalf. */
    public BankAccount createOnBehalfOf(UUID businessId, CreateBankAccountRequest request) {
        requireBusinessExists(businessId);
        return createForBusiness(businessId, request);
    }

    @Transactional(readOnly = true)
    public Page<BankAccount> listForCallerBusiness(Pageable pageable) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        return bankAccountRepository.findByBusinessId(currentUser.businessId(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<BankAccount> listForBusiness(UUID businessId, Pageable pageable) {
        requireBusinessExists(businessId);
        return bankAccountRepository.findByBusinessId(businessId, pageable);
    }

    private BankAccount createForBusiness(UUID businessId, CreateBankAccountRequest request) {
        // Checked before Name Enquiry so a duplicate never spends a provider call.
        if (bankAccountRepository.existsByBusinessIdAndBankIdAndAccountNumberAndStatus(
                businessId, request.bankId(), request.accountNumber(), EntityStatus.ACTIVE)) {
            throw new ApiException(ErrorCode.BANK_ACCOUNT_ALREADY_REGISTERED);
        }
        // Name Enquiry (doc 3 §3): the account holder's name is always the
        // provider-resolved one, never client-supplied - closes a
        // misdirected-funds spoofing vector where a caller claims any name.
        ResolvedAccount resolved = bankVerificationService.resolve(request.bankId(), request.accountNumber());
        BankAccount bankAccount = new BankAccount(request.bankId(), resolved.accountNumber(), resolved.accountName(),
                businessId);
        return bankAccountRepository.save(bankAccount);
    }

    private void requireBusinessExists(UUID businessId) {
        if (!bankAccountRepository.businessExists(businessId)) {
            throw new ApiException(ErrorCode.BUSINESS_NOT_FOUND);
        }
    }
}
