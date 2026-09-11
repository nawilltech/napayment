package ng.com.nawill.pay.onboarding.user;

import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.onboarding.business.Business;
import ng.com.nawill.pay.onboarding.business.BusinessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final CurrentUserResolver currentUserResolver;

    public UserService(UserRepository userRepository, BusinessRepository businessRepository,
                        CurrentUserResolver currentUserResolver) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.currentUserResolver = currentUserResolver;
    }

    public UserResponse me() {
        CurrentUser currentUser = currentUserResolver.requireCurrentUser();
        User user = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + currentUser.userId()));
        Business business = user.getBusinessId() == null ? null : businessRepository.findById(user.getBusinessId())
                .orElse(null);
        return UserResponse.from(user, business);
    }
}
