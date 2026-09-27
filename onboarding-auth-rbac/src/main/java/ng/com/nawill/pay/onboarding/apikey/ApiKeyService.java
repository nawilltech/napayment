package ng.com.nawill.pay.onboarding.apikey;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import ng.com.nawill.pay.common.crypto.EncryptionService;
import ng.com.nawill.pay.common.entity.EntityStatus;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.common.web.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
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
            throw new ApiException(ErrorCode.API_KEY_ALREADY_EXISTS);
        }
        return issue(businessId);
    }

    public ApiKeyGeneratedResponse regenerate() {
        UUID businessId = currentUserResolver.requireBusinessScope().businessId();
        apiKeyRepository.findByBusinessIdAndStatus(businessId, EntityStatus.ACTIVE).ifPresent(existing -> {
            existing.setStatus(EntityStatus.INACTIVE);
            // Flush now: Hibernate orders INSERTs before UPDATEs at flush time, so without this the new
            // key's insert hits idx_api_keys_business_id_active while the old key still reads ACTIVE.
            apiKeyRepository.saveAndFlush(existing);
        });
        return issue(businessId);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApiKeyResponse> list(Pageable pageable) {
        UUID businessId = currentUserResolver.requireBusinessScope().businessId();
        return PageResponse.from(apiKeyRepository.findByBusinessIdAndStatus(businessId, EntityStatus.ACTIVE, pageable)
                .map(ApiKeyResponse::from));
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
    public PageResponse<String> listWhitelist(Pageable pageable) {
        ApiKeyCredential apiKey = requireActiveKey();
        return PageResponse.from(
                ipWhitelistRepository.findByApiKeyId(apiKey.getId(), pageable).map(ApiKeyIpWhitelist::getCidr));
    }

    /**
     * FR-9's "configure a webhook URL". Reachability is deliberately not
     * checked at save time (contract note: a business may configure this
     * before their endpoint is live) - only that a non-blank value is a
     * well-formed absolute HTTP(S) URL.
     */
    public WebhookConfigResponse updateWebhookConfig(WebhookConfigRequest request) {
        ApiKeyCredential apiKey = requireActiveKey();
        String callbackUrl = validateUrl(request.callbackUrl());
        String webhookUrl = validateUrl(request.webhookUrl());
        apiKey.updateWebhookConfig(callbackUrl, webhookUrl);
        apiKey = apiKeyRepository.save(apiKey);
        log.info("webhook config updated: apiKeyId={}", apiKey.getId());
        return WebhookConfigResponse.from(apiKey);
    }

    @Transactional(readOnly = true)
    public WebhookConfigResponse getWebhookConfig() {
        return WebhookConfigResponse.from(requireActiveKey());
    }

    private String validateUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI parsed = new URI(url);
            if (parsed.getScheme() == null || !parsed.getScheme().matches("https?") || parsed.getHost() == null) {
                throw new ApiException(ErrorCode.INVALID_URL);
            }
        } catch (URISyntaxException e) {
            throw new ApiException(ErrorCode.INVALID_URL);
        }
        return url;
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
                .orElseThrow(() -> new ApiException(ErrorCode.API_KEY_NOT_FOUND));
    }

    private String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
