package ng.com.nawill.pay.onboarding.user;

import java.util.Optional;
import java.util.UUID;
import ng.com.nawill.pay.payments.transfer.RecipientDirectory;
import org.springframework.stereotype.Component;

/** Implements payments' {@link RecipientDirectory} seam (doc 2 §7 ADR-12) - backed by {@link UserRepository}. */
@Component
public class UserRecipientDirectory implements RecipientDirectory {

    private final UserRepository userRepository;

    public UserRecipientDirectory(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<UUID> resolveUserIdByPhone(String phoneNo) {
        return userRepository.findByPhoneNo(phoneNo).map(User::getId);
    }

    @Override
    public Optional<RecipientName> nameFor(UUID userId) {
        return userRepository.findById(userId).map(u -> new RecipientName(u.getFirstName(), u.getLastName()));
    }
}
