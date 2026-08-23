package ng.com.nawill.pay.payments.collectionaccount;

import ng.com.nawill.pay.common.entity.EntityStatus;
import ng.com.nawill.pay.common.exception.ConflictException;
import ng.com.nawill.pay.common.exception.ResourceNotFoundException;
import ng.com.nawill.pay.referencedata.repository.BankRepository;
import ng.com.nawill.pay.common.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CollectionAccountService {

    private final CollectionAccountRepository collectionAccountRepository;
    private final BankRepository bankRepository;

    public CollectionAccountService(CollectionAccountRepository collectionAccountRepository, BankRepository bankRepository) {
        this.collectionAccountRepository = collectionAccountRepository;
        this.bankRepository = bankRepository;
    }

    public CollectionAccount create(CreateCollectionAccountRequest request) {
        if (collectionAccountRepository.findByStatus(EntityStatus.ACTIVE).isPresent()) {
            throw new ConflictException("A collection account is already active - deactivate it before creating another");
        }
        if (!bankRepository.existsById(request.bankId())) {
            throw new BadRequestException("UNKNOWN_BANK", "Unknown bank: " + request.bankId());
        }
        CollectionAccount account = new CollectionAccount(request.bankId(), request.accountNumber(), request.accountName());
        return collectionAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public CollectionAccount getActive() {
        return collectionAccountRepository.findByStatus(EntityStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active collection account has been configured"));
    }
}
