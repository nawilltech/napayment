package ng.com.nawill.pay.onboarding.apikey;

import java.util.Optional;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.EntityStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKeyCredential, UUID> {

    Optional<ApiKeyCredential> findByPublicKey(String publicKey);

    Optional<ApiKeyCredential> findByBusinessIdAndStatus(UUID businessId, EntityStatus status);
}
