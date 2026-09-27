package ng.com.nawill.pay.payments.collectionaccount;

import ng.com.nawill.pay.common.entity.EntityStatus;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.payments.bankverification.BankVerificationGateway.ResolvedAccount;
import ng.com.nawill.pay.payments.bankverification.BankVerificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CollectionAccountService {

    private final CollectionAccountRepository collectionAccountRepository;
    private final BankVerificationService bankVerificationService;

    public CollectionAccountService(CollectionAccountRepository collectionAccountRepository,
                                    BankVerificationService bankVerificationService) {
        this.collectionAccountRepository = collectionAccountRepository;
        this.bankVerificationService = bankVerificationService;
    }

    public CollectionAccount create(CreateCollectionAccountRequest request) {
        if (collectionAccountRepository.findByStatus(EntityStatus.ACTIVE).isPresent()) {
            throw new ApiException(ErrorCode.COLLECTION_ACCOUNT_ALREADY_ACTIVE);
        }
        // Name Enquiry also validates bankId (UNKNOWN_BANK) - the pooled
        // account's holder name is provider-resolved, same as a business's
        // settlement bank account.
        ResolvedAccount resolved = bankVerificationService.resolve(request.bankId(), request.accountNumber());
        CollectionAccount account = new CollectionAccount(request.bankId(), resolved.accountNumber(), resolved.accountName());
        return collectionAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public CollectionAccount getActive() {
        return collectionAccountRepository.findByStatus(EntityStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.COLLECTION_ACCOUNT_NOT_FOUND));
    }
}
