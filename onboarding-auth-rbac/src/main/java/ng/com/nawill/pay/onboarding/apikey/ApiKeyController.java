package ng.com.nawill.pay.onboarding.apikey;

import jakarta.validation.Valid;
import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @PreAuthorize("@auth.can('apikeys:manage')")
    public ResponseEntity<ApiKeyGeneratedResponse> generate() {
        return ResponseEntity.status(HttpStatus.CREATED).body(apiKeyService.generate());
    }

    @PostMapping("/regenerate")
    @PreAuthorize("@auth.can('apikeys:manage')")
    public ApiKeyGeneratedResponse regenerate() {
        return apiKeyService.regenerate();
    }

    @GetMapping
    @PreAuthorize("@auth.can('apikeys:manage')")
    public PageResponse<ApiKeyResponse> list(@RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                              @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return apiKeyService.list(PageRequest.of(page, size));
    }

    @PostMapping("/ip-whitelist")
    @PreAuthorize("@auth.can('apikeys:manage')")
    public ResponseEntity<Void> addIpToWhitelist(@Valid @RequestBody IpWhitelistRequest request) {
        apiKeyService.addIpToWhitelist(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/ip-whitelist")
    @PreAuthorize("@auth.can('apikeys:manage')")
    public PageResponse<String> listIpWhitelist(@RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                 @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return apiKeyService.listWhitelist(PageRequest.of(page, size));
    }

    @DeleteMapping("/ip-whitelist")
    @PreAuthorize("@auth.can('apikeys:manage')")
    public ResponseEntity<Void> removeIpFromWhitelist(@RequestParam String cidr) {
        apiKeyService.removeIpFromWhitelist(cidr);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/webhook-config")
    @PreAuthorize("@auth.can('apikeys:manage')")
    public WebhookConfigResponse updateWebhookConfig(@RequestBody WebhookConfigRequest request) {
        return apiKeyService.updateWebhookConfig(request);
    }

    @GetMapping("/webhook-config")
    @PreAuthorize("@auth.can('apikeys:manage')")
    public WebhookConfigResponse getWebhookConfig() {
        return apiKeyService.getWebhookConfig();
    }
}
