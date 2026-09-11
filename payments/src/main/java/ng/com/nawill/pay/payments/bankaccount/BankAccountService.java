package ng.com.nawill.pay.payments.bankaccount;

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
        // Name Enquiry (doc 3 §3): the account holder's name is always the
        // provider-resolved one, never client-supplied - closes a
        // misdirected-funds spoofing vector where a caller claims any name.
        ResolvedAccount resolved = bankVerificationService.resolve(request.bankId(), request.accountNumber());
        BankAccount bankAccount = new BankAccount(request.bankId(), resolved.accountNumber(), resolved.accountName(),
                currentUser.businessId());
        return bankAccountRepository.save(bankAccount);
    }

    @Transactional(readOnly = true)
    public Page<BankAccount> listForCallerBusiness(Pageable pageable) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        return bankAccountRepository.findByBusinessId(currentUser.businessId(), pageable);
    }
}
