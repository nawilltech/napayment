package ng.com.nawill.pay.payments.bankaccount;

import ng.com.nawill.pay.common.exception.BadRequestException;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.referencedata.repository.BankRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final BankRepository bankRepository;
    private final CurrentUserResolver currentUserResolver;

    public BankAccountService(BankAccountRepository bankAccountRepository, BankRepository bankRepository,
                               CurrentUserResolver currentUserResolver) {
        this.bankAccountRepository = bankAccountRepository;
        this.bankRepository = bankRepository;
        this.currentUserResolver = currentUserResolver;
    }

    public BankAccount create(CreateBankAccountRequest request) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        if (!bankRepository.existsById(request.bankId())) {
            throw new BadRequestException("UNKNOWN_BANK", "Unknown bank: " + request.bankId());
        }
        BankAccount bankAccount = new BankAccount(request.bankId(), request.accountNumber(), request.accountName(),
                currentUser.businessId());
        return bankAccountRepository.save(bankAccount);
    }

    @Transactional(readOnly = true)
    public Page<BankAccount> listForCallerBusiness(Pageable pageable) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        return bankAccountRepository.findByBusinessId(currentUser.businessId(), pageable);
    }
}
