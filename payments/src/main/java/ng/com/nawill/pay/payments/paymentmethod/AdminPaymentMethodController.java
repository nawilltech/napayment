package ng.com.nawill.pay.payments.paymentmethod;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.payments.processor.PasswordConfirmationRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The platform's payment method catalogue (FR-Proc-2). */
@Tag(name = "Admin - Payment Methods", description = "The payment methods processors can offer.")
@RestController
@RequestMapping("/api/v1/admin/payment-methods")
public class AdminPaymentMethodController {

    private final PaymentMethodService service;

    public AdminPaymentMethodController(PaymentMethodService service) {
        this.service = service;
    }

    @Operation(summary = "List payment methods in display order - archived ones only with archived=true")
    @GetMapping
    @PreAuthorize("@auth.can('platform-processors:read')")
    public List<PaymentMethodResponse> list(@RequestParam(defaultValue = "false") boolean archived) {
        return service.list(archived);
    }

    @Operation(summary = "Get a payment method")
    @GetMapping("/{id}")
    @PreAuthorize("@auth.can('platform-processors:read')")
    public PaymentMethodResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Add a payment method")
    @PostMapping
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public ResponseEntity<PaymentMethodResponse> create(@Valid @RequestBody CreatePaymentMethodRequest request) {
        PaymentMethodResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/payment-methods/" + created.id())).body(created);
    }

    @Operation(summary = "Rename a payment method or change its description or display order")
    @PatchMapping("/{id}")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public PaymentMethodResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePaymentMethodRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Activate a payment method platform-wide (requires password)")
    @PostMapping("/{id}/activate")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public PaymentMethodResponse activate(@PathVariable UUID id, @Valid @RequestBody PasswordConfirmationRequest request) {
        return service.setActive(id, true, request.password());
    }

    @Operation(summary = "Deactivate a payment method platform-wide (requires password)")
    @PostMapping("/{id}/deactivate")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public PaymentMethodResponse deactivate(@PathVariable UUID id, @Valid @RequestBody PasswordConfirmationRequest request) {
        return service.setActive(id, false, request.password());
    }

    @Operation(summary = "Archive a payment method: deactivate and hide it, keeping history (requires password)")
    @PostMapping("/{id}/archive")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public PaymentMethodResponse archive(@PathVariable UUID id, @Valid @RequestBody PasswordConfirmationRequest request) {
        return service.archive(id, request.password());
    }

    @Operation(summary = "Restore an archived payment method (it stays inactive until reactivated)")
    @PostMapping("/{id}/restore")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public PaymentMethodResponse restore(@PathVariable UUID id) {
        return service.restore(id);
    }
}
