package ng.com.nawill.pay.onboarding.apikey;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyIpWhitelistRepository extends JpaRepository<ApiKeyIpWhitelist, UUID> {

    List<ApiKeyIpWhitelist> findByApiKeyId(UUID apiKeyId);

    void deleteByApiKeyIdAndCidr(UUID apiKeyId, String cidr);
}
