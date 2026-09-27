package ng.com.nawill.pay.onboarding.business;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BusinessRepository extends JpaRepository<Business, UUID>, JpaSpecificationExecutor<Business> {

    long countByKycStatus(KycStatus kycStatus);
}
