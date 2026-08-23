package ng.com.nawill.pay.payments.virtualaccount;

import java.util.UUID;
import ng.com.nawill.pay.payments.util.AccountNumberGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the payments module for FR-1 ("On successful signup, the
 * platform shall automatically provision a virtual account"). Called
 * synchronously by onboarding-auth-rbac's signup flow, inside the same
 * transaction, so a provisioning failure rolls back the signup.
 */
@Service
public class VirtualAccountProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(VirtualAccountProvisioningService.class);
    private static final String DEFAULT_CURRENCY = "NGN";
    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final VirtualAccountRepository virtualAccountRepository;
    private final AccountNumberGenerator accountNumberGenerator;

    public VirtualAccountProvisioningService(VirtualAccountRepository virtualAccountRepository,
                                              AccountNumberGenerator accountNumberGenerator) {
        this.virtualAccountRepository = virtualAccountRepository;
        this.accountNumberGenerator = accountNumberGenerator;
    }

    @Transactional
    public VirtualAccountResponse provisionForUser(UUID userId) {
        String accountNumber = accountNumberGenerator.generateUnique(virtualAccountRepository::existsByAccountNumber);
        VirtualAccount saved = save(new VirtualAccount(accountNumber, userId, null, DEFAULT_CURRENCY));
        log.info("virtual account provisioned for user");
        return VirtualAccountResponse.from(saved);
    }

    @Transactional
    public VirtualAccountResponse provisionForBusiness(UUID businessId) {
        String accountNumber = accountNumberGenerator.generateUnique(virtualAccountRepository::existsByAccountNumber);
        VirtualAccount saved = save(new VirtualAccount(accountNumber, null, businessId, DEFAULT_CURRENCY));
        log.info("virtual account provisioned for business");
        return VirtualAccountResponse.from(saved);
    }

    private VirtualAccount save(VirtualAccount account) {
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            try {
                return virtualAccountRepository.saveAndFlush(account);
            } catch (DataIntegrityViolationException e) {
                if (attempt == MAX_GENERATION_ATTEMPTS) {
                    throw e;
                }
                account.reassignAccountNumber(accountNumberGenerator.generateUnique(virtualAccountRepository::existsByAccountNumber));
            }
        }
        throw new IllegalStateException("unreachable");
    }
}
