package ng.com.nawill.pay.onboarding.apikey;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.common.crypto.EncryptionService;
import ng.com.nawill.pay.common.entity.EntityStatus;
import ng.com.nawill.pay.common.exception.BadRequestException;
import ng.com.nawill.pay.common.exception.ResourceNotFoundException;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business-scoped API key pair issuance/rotation (FR-9, doc 2 §2.1). One
 * active pair per business at a time - {@link #regenerate()} revokes the old
 * pair (status INACTIVE, kept for audit) rather than deleting it.
 */
@Service
@Transactional
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String PUBLIC_KEY_PREFIX = "pk_live_";
    private static final String SECRET_KEY_PREFIX = "sk_live_";

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyIpWhitelistRepository ipWhitelistRepository;
    private final EncryptionService encryptionService;
    private final CurrentUserResolver currentUserResolver;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository, ApiKeyIpWhitelistRepository ipWhitelistRepository,
                          EncryptionService encryptionService, CurrentUserResolver currentUserResolver) {
        this.apiKeyRepository = apiKeyRepository;
        this.ipWhitelistRepository = ipWhitelistRepository;
        this.encryptionService = encryptionService;
        this.currentUserResolver = currentUserResolver;
    }

    public ApiKeyGeneratedResponse generate() {
        UUID businessId = currentUserResolver.requireBusinessScope().businessId();
        if (apiKeyRepository.findByBusinessIdAndStatus(businessId, EntityStatus.ACTIVE).isPresent()) {
            throw new BadRequestException("API_KEY_ALREADY_EXISTS",
                    "An active API key pair already exists for this business - use regenerate instead");
        }
        return issue(businessId);
    }

    public ApiKeyGeneratedResponse regenerate() {
        UUID businessId = currentUserResolver.requireBusinessScope().businessId();
        apiKeyRepository.findByBusinessIdAndStatus(businessId, EntityStatus.ACTIVE).ifPresent(existing -> {
            existing.setStatus(EntityStatus.INACTIVE);
            apiKeyRepository.save(existing);
        });
        return issue(businessId);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list() {
        UUID businessId = currentUserResolver.requireBusinessScope().businessId();
        return apiKeyRepository.findByBusinessIdAndStatus(businessId, EntityStatus.ACTIVE)
                .map(key -> List.of(ApiKeyResponse.from(key)))
                .orElseGet(List::of);
    }

    public void addIpToWhitelist(IpWhitelistRequest request) {
        ApiKeyCredential apiKey = requireActiveKey();
        ipWhitelistRepository.save(new ApiKeyIpWhitelist(apiKey, request.cidr()));
        log.info("IP added to API key whitelist: apiKeyId={}", apiKey.getId());
    }

    public void removeIpFromWhitelist(String cidr) {
        ApiKeyCredential apiKey = requireActiveKey();
        ipWhitelistRepository.deleteByApiKeyIdAndCidr(apiKey.getId(), cidr);
        log.info("IP removed from API key whitelist: apiKeyId={}", apiKey.getId());
    }

    @Transactional(readOnly = true)
    public List<String> listWhitelist() {
        ApiKeyCredential apiKey = requireActiveKey();
        return ipWhitelistRepository.findByApiKeyId(apiKey.getId()).stream().map(ApiKeyIpWhitelist::getCidr).toList();
    }

    private ApiKeyGeneratedResponse issue(UUID businessId) {
        String publicKey = PUBLIC_KEY_PREFIX + randomHex(16);
        String secretKey = SECRET_KEY_PREFIX + randomHex(24);

        ApiKeyCredential apiKey = new ApiKeyCredential(businessId, publicKey, encryptionService.encrypt(secretKey));
        apiKey = apiKeyRepository.save(apiKey);
        log.info("API key pair issued: apiKeyId={} businessId={}", apiKey.getId(), businessId);

        return new ApiKeyGeneratedResponse(apiKey.getId(), publicKey, secretKey);
    }

    private ApiKeyCredential requireActiveKey() {
        UUID businessId = currentUserResolver.requireBusinessScope().businessId();
        return apiKeyRepository.findByBusinessIdAndStatus(businessId, EntityStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active API key for this business - generate one first"));
    }

    private String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
