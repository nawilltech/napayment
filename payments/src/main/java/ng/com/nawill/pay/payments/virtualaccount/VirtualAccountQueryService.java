package ng.com.nawill.pay.payments.virtualaccount;

import java.util.List;
import ng.com.nawill.pay.common.exception.ResourceNotFoundException;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business-scoped reads over the caller's own virtual account(s) (doc 3 §2.2). */
@Service
@Transactional(readOnly = true)
public class VirtualAccountQueryService {

    private final VirtualAccountRepository virtualAccountRepository;
    private final CurrentUserResolver currentUserResolver;

    public VirtualAccountQueryService(VirtualAccountRepository virtualAccountRepository,
                                       CurrentUserResolver currentUserResolver) {
        this.virtualAccountRepository = virtualAccountRepository;
        this.currentUserResolver = currentUserResolver;
    }

    public List<VirtualAccount> listForCaller() {
        CurrentUser currentUser = currentUserResolver.requireCurrentUser();
        return currentUser.hasBusinessScope()
                ? virtualAccountRepository.findByBusinessId(currentUser.businessId())
                : virtualAccountRepository.findByUserId(currentUser.userId());
    }

    public Page<VirtualAccount> listForCaller(Pageable pageable) {
        CurrentUser currentUser = currentUserResolver.requireCurrentUser();
        return currentUser.hasBusinessScope()
                ? virtualAccountRepository.findByBusinessId(currentUser.businessId(), pageable)
                : virtualAccountRepository.findByUserId(currentUser.userId(), pageable);
    }

    /**
     * Single source of truth for "resolve the caller's one virtual account"
     * - every feature built on top of an existing virtual account (settlement
     * accounts, payment links, dynamic accounts, ...) uses this instead of
     * re-deriving the same business/user lookup + not-found handling. The
     * system provisions exactly one virtual account per user/business today
     * (FR-1); this is the one place that assumption lives.
     */
    public VirtualAccount requireSoleVirtualAccountForCaller() {
        return listForCaller().stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No virtual account found for the caller"));
    }
}
