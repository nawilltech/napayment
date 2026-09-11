package ng.com.nawill.pay.onboarding.kyc;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerIdentityRepository extends JpaRepository<OwnerIdentity, UUID> {

    Optional<OwnerIdentity> findByBusinessId(UUID businessId);
}
