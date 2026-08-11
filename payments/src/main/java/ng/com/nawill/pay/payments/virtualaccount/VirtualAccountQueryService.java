package ng.com.nawill.pay.payments.virtualaccount;

import java.util.List;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
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
}
