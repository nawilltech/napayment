package ng.com.nawill.pay.onboarding.apikey;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyIpWhitelistRepository extends JpaRepository<ApiKeyIpWhitelist, UUID> {

    // Used by ApiKeyAuthenticationFilter (per-request hot path) - needs the
    // full whitelist, never a page of it, so this stays List-returning.
    List<ApiKeyIpWhitelist> findByApiKeyId(UUID apiKeyId);

    // Paginated overload for the list endpoint only.
    Page<ApiKeyIpWhitelist> findByApiKeyId(UUID apiKeyId, Pageable pageable);

    void deleteByApiKeyIdAndCidr(UUID apiKeyId, String cidr);
}
