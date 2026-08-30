package ng.com.nawill.pay.onboarding.apikey;

import java.util.Optional;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKeyCredential, UUID> {

    Optional<ApiKeyCredential> findByPublicKey(String publicKey);

    Optional<ApiKeyCredential> findByBusinessIdAndStatus(UUID businessId, EntityStatus status);

    // Paginated overload for the list endpoint only - the Optional-returning
    // form above is relied on by security-critical single-lookup call sites
    // (requireActiveKey, the duplicate-active-key check in generate()) and
    // must not be touched.
    Page<ApiKeyCredential> findByBusinessIdAndStatus(UUID businessId, EntityStatus status, Pageable pageable);
}
