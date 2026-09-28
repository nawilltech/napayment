package ng.com.nawill.pay.payments.processor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform processor catalogue and its platform-wide switches (FR-Proc-1/2, FR-Admin-5). */
@Tag(name = "Admin - Payment Processors", description = "Platform payment processors, their payment methods and platform-wide switches.")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminPaymentProcessorController {

    private final PaymentProcessorService service;

    public AdminPaymentProcessorController(PaymentProcessorService service) {
        this.service = service;
    }

    @Operation(summary = "Add a payment processor with its payment methods")
    @PostMapping("/payment-processors")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public ResponseEntity<PaymentProcessorResponse> create(@Valid @RequestBody CreatePaymentProcessorRequest request) {
        PaymentProcessorResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/payment-processors/" + created.id())).body(created);
    }

    @Operation(summary = "List payment processors by priority - archived ones only with archived=true")
    @GetMapping("/payment-processors")
    @PreAuthorize("@auth.can('platform-processors:read')")
    public PageResponse<PaymentProcessorResponse> list(@RequestParam(required = false) String term,
                                                       @RequestParam(defaultValue = "false") boolean archived,
                                                       @RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                       @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        Sort byPriority = Sort.by("priority").and(Sort.by("name"));
        return PageResponse.from(service.list(term, archived, PageRequest.of(page, size, byPriority)));
    }

    @Operation(summary = "Get a payment processor")
    @GetMapping("/payment-processors/{id}")
    @PreAuthorize("@auth.can('platform-processors:read')")
    public PaymentProcessorResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Rename a processor or change its routing priority")
    @PatchMapping("/payment-processors/{id}")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public PaymentProcessorResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePaymentProcessorRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Archive a processor: deactivate and hide it, keeping history (requires password)")
    @PostMapping("/payment-processors/{id}/archive")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public PaymentProcessorResponse archive(@PathVariable UUID id, @Valid @RequestBody PasswordConfirmationRequest request) {
        return service.archive(id, request.password());
    }

    @Operation(summary = "Restore an archived processor (it stays inactive until reactivated)")
    @PostMapping("/payment-processors/{id}/restore")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public PaymentProcessorResponse restore(@PathVariable UUID id) {
        return service.restore(id);
    }

    @Operation(summary = "Set a processor's logo (base64 data URL: PNG, JPEG or WebP, max 100 KB)")
    @PutMapping("/payment-processors/{id}/logo")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public PaymentProcessorResponse setLogo(@PathVariable UUID id, @Valid @RequestBody SetProcessorLogoRequest request) {
        return service.setLogo(id, request.logo());
    }

    @Operation(summary = "Remove a processor's logo")
    @DeleteMapping("/payment-processors/{id}/logo")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public PaymentProcessorResponse removeLogo(@PathVariable UUID id) {
        return service.setLogo(id, null);
    }

    @Operation(summary = "Add or re-enable a payment method on a processor")
    @PutMapping("/payment-processors/{id}/methods/{method}")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public PaymentProcessorResponse enableMethod(@PathVariable UUID id, @PathVariable String method) {
        return service.enableMethod(id, method);
    }

    @Operation(summary = "Disable a payment method on a processor (history kept)")
    @DeleteMapping("/payment-processors/{id}/methods/{method}")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public PaymentProcessorResponse disableMethod(@PathVariable UUID id, @PathVariable String method) {
        return service.disableMethod(id, method);
    }

    @Operation(summary = "Activate a processor platform-wide (requires password)")
    @PostMapping("/payment-processors/{id}/activate")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public PaymentProcessorResponse activate(@PathVariable UUID id, @Valid @RequestBody PasswordConfirmationRequest request) {
        return service.setActive(id, true, request.password());
    }

    @Operation(summary = "Deactivate a processor platform-wide (requires password)")
    @PostMapping("/payment-processors/{id}/deactivate")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public PaymentProcessorResponse deactivate(@PathVariable UUID id, @Valid @RequestBody PasswordConfirmationRequest request) {
        return service.setActive(id, false, request.password());
    }

    @Operation(summary = "Switch a processor ON for all businesses, clearing their own settings (requires password)")
    @PostMapping("/payment-processors/{id}/enable-for-all-businesses")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public ForAllBusinessesResponse enableForAll(@PathVariable UUID id, @Valid @RequestBody PasswordConfirmationRequest request) {
        return service.setForAllBusinesses(id, true, request.password());
    }

    @Operation(summary = "Switch a processor OFF for all businesses, clearing their own settings (requires password)")
    @PostMapping("/payment-processors/{id}/disable-for-all-businesses")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public ForAllBusinessesResponse disableForAll(@PathVariable UUID id, @Valid @RequestBody PasswordConfirmationRequest request) {
        return service.setForAllBusinesses(id, false, request.password());
    }
}
