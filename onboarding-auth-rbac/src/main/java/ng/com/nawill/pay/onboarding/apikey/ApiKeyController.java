package ng.com.nawill.pay.onboarding.apikey;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "API Keys", description = "The business's API key pair, IP whitelist and webhook settings.")
@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Operation(summary = "Generate the business's API key pair")
    @PostMapping
    @PreAuthorize("@auth.can('apikeys:manage')")
    public ResponseEntity<ApiKeyGeneratedResponse> generate() {
        return ResponseEntity.status(HttpStatus.CREATED).body(apiKeyService.generate());
    }

    @Operation(summary = "Regenerate the API key pair, invalidating the old one")
    @PostMapping("/regenerate")
    @PreAuthorize("@auth.can('apikeys:manage')")
    public ResponseEntity<ApiKeyGeneratedResponse> regenerate() {
        // A new key pair is created, same as generate().
        return ResponseEntity.status(HttpStatus.CREATED).body(apiKeyService.regenerate());
    }

    @Operation(summary = "List the business's API keys")
    @GetMapping
    @PreAuthorize("@auth.can('apikeys:manage')")
    public PageResponse<ApiKeyResponse> list(@RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                              @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return apiKeyService.list(PageRequest.of(page, size));
    }

    @Operation(summary = "Add an IP/CIDR to the API key whitelist")
    @PostMapping("/ip-whitelist")
    @PreAuthorize("@auth.can('apikeys:manage')")
    public ResponseEntity<Void> addIpToWhitelist(@Valid @RequestBody IpWhitelistRequest request) {
        apiKeyService.addIpToWhitelist(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "List whitelisted IPs for API key calls")
    @GetMapping("/ip-whitelist")
    @PreAuthorize("@auth.can('apikeys:manage')")
    public PageResponse<String> listIpWhitelist(@RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                 @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return apiKeyService.listWhitelist(PageRequest.of(page, size));
    }

    @Operation(summary = "Remove an IP/CIDR from the whitelist")
    @DeleteMapping("/ip-whitelist")
    @PreAuthorize("@auth.can('apikeys:manage')")
    public ResponseEntity<Void> removeIpFromWhitelist(@RequestParam String cidr) {
        apiKeyService.removeIpFromWhitelist(cidr);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Set the webhook URLs for API key events")
    @PutMapping("/webhook-config")
    @PreAuthorize("@auth.can('apikeys:manage')")
    public WebhookConfigResponse updateWebhookConfig(@RequestBody WebhookConfigRequest request) {
        return apiKeyService.updateWebhookConfig(request);
    }

    @Operation(summary = "Get the configured webhook URLs")
    @GetMapping("/webhook-config")
    @PreAuthorize("@auth.can('apikeys:manage')")
    public WebhookConfigResponse getWebhookConfig() {
        return apiKeyService.getWebhookConfig();
    }
}
