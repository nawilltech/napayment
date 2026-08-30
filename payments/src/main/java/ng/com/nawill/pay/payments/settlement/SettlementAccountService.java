package ng.com.nawill.pay.payments.settlement;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.BadRequestException;
import ng.com.nawill.pay.common.exception.ResourceNotFoundException;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.payments.bankaccount.BankAccount;
import ng.com.nawill.pay.payments.bankaccount.BankAccountRepository;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccount;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccountQueryService;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SettlementAccountService {

    private static final BigDecimal MAX_TOTAL_PERCENTAGE = BigDecimal.valueOf(100);

    private final SettlementAccountRepository settlementAccountRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final VirtualAccountQueryService virtualAccountQueryService;
    private final BankAccountRepository bankAccountRepository;
    private final CurrentUserResolver currentUserResolver;

    public SettlementAccountService(SettlementAccountRepository settlementAccountRepository,
                                     VirtualAccountRepository virtualAccountRepository,
                                     VirtualAccountQueryService virtualAccountQueryService,
                                     BankAccountRepository bankAccountRepository,
                                     CurrentUserResolver currentUserResolver) {
        this.settlementAccountRepository = settlementAccountRepository;
        this.virtualAccountRepository = virtualAccountRepository;
        this.virtualAccountQueryService = virtualAccountQueryService;
        this.bankAccountRepository = bankAccountRepository;
        this.currentUserResolver = currentUserResolver;
    }

    public SettlementAccount create(CreateSettlementAccountRequest request) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        UUID callerVirtualAccountId = virtualAccountQueryService.requireSoleVirtualAccountForCaller().getId();

        // Lock the virtual account for the whole validate-then-insert so two
        // concurrent creates for the same account can never both pass the
        // sum-<=100% check before either commits.
        VirtualAccount virtualAccount = virtualAccountRepository.findByIdForUpdate(callerVirtualAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Virtual account not found: " + callerVirtualAccountId));

        BankAccount bankAccount = bankAccountRepository.findById(request.bankAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found: " + request.bankAccountId()));
        if (!bankAccount.isOwnedByBusiness(currentUser.businessId())) {
            throw new BadRequestException("Bank account does not belong to the caller's business");
        }

        BigDecimal existingTotal = settlementAccountRepository.findByVirtualAccountId(virtualAccount.getId()).stream()
                .map(SettlementAccount::getSplitPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal newTotal = existingTotal.add(request.splitPercentage());
        if (newTotal.compareTo(MAX_TOTAL_PERCENTAGE) > 0) {
            throw new BadRequestException("SPLIT_PERCENTAGE_EXCEEDED",
                    "Total split percentage for this virtual account would be " + newTotal + "%, exceeding 100%");
        }

        return settlementAccountRepository.save(new SettlementAccount(virtualAccount, bankAccount, request.splitPercentage()));
    }

    @Transactional(readOnly = true)
    public Page<SettlementAccount> listForCallerVirtualAccount(Pageable pageable) {
        currentUserResolver.requireBusinessScope();
        return settlementAccountRepository.findByVirtualAccountId(
                virtualAccountQueryService.requireSoleVirtualAccountForCaller().getId(), pageable);
    }

    public void toggleAutoSettle(AutoSettleToggleRequest request) {
        currentUserResolver.requireBusinessScope();
        VirtualAccount virtualAccount = virtualAccountQueryService.requireSoleVirtualAccountForCaller();
        virtualAccount.setAutoSettle(request.autoSettle());
    }
}
