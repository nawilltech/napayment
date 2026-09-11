package ng.com.nawill.pay.onboarding.business;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessContactSettingsRepository extends JpaRepository<BusinessContactSettings, UUID> {

    Optional<BusinessContactSettings> findByBusinessId(UUID businessId);
}
