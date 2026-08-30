package ng.com.nawill.pay.payments.bankverification;

import java.util.UUID;
import ng.com.nawill.pay.common.exception.BadRequestException;
import ng.com.nawill.pay.payments.bankverification.BankVerificationGateway.ResolvedAccount;
import ng.com.nawill.pay.referencedata.entity.Bank;
import ng.com.nawill.pay.referencedata.repository.BankRepository;
import org.springframework.stereotype.Service;

/** Shared by {@link BankVerificationController} and bank-account creation. */
@Service
public class BankVerificationService {

    private final BankVerificationGateway gateway;
    private final BankRepository bankRepository;

    public BankVerificationService(BankVerificationGateway gateway, BankRepository bankRepository) {
        this.gateway = gateway;
        this.bankRepository = bankRepository;
    }

    public ResolvedAccount resolve(UUID bankId, String accountNumber) {
        Bank bank = bankRepository.findById(bankId)
                .orElseThrow(() -> new BadRequestException("UNKNOWN_BANK", "Unknown bank: " + bankId));
        return gateway.resolveAccountName(accountNumber, bank.getCode());
    }
}
