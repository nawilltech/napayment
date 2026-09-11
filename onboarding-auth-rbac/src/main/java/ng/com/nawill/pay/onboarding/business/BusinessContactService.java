package ng.com.nawill.pay.onboarding.business;

import java.util.List;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.onboarding.user.User;
import ng.com.nawill.pay.onboarding.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BusinessContactService {

    private final BusinessContactSettingsRepository businessContactSettingsRepository;
    private final UserRepository userRepository;
    private final CurrentUserResolver currentUserResolver;

    public BusinessContactService(BusinessContactSettingsRepository businessContactSettingsRepository,
                                   UserRepository userRepository, CurrentUserResolver currentUserResolver) {
        this.businessContactSettingsRepository = businessContactSettingsRepository;
        this.userRepository = userRepository;
        this.currentUserResolver = currentUserResolver;
    }

    public BusinessContactResponse update(BusinessContactRequest request) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        BusinessContactSettings settings = businessContactSettingsRepository.findByBusinessId(currentUser.businessId())
                .orElseGet(() -> new BusinessContactSettings(currentUser.businessId()));
        settings.update(request.disputeEmails(), request.refundEmails(), request.supportEmail(), request.generalEmail());
        settings = businessContactSettingsRepository.save(settings);
        return BusinessContactResponse.from(settings);
    }

    @Transactional(readOnly = true)
    public BusinessContactResponse get() {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        return businessContactSettingsRepository.findByBusinessId(currentUser.businessId())
                .map(BusinessContactResponse::from)
                .orElseGet(() -> defaultResponse(currentUser));
    }

    private BusinessContactResponse defaultResponse(CurrentUser currentUser) {
        String email = userRepository.findById(currentUser.userId()).map(User::getEmail).orElse("");
        return new BusinessContactResponse(List.of(), List.of(), "", email);
    }
}
