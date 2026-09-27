package ng.com.nawill.pay.payments.processor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Onboarding/activation of payment processors (FR-6) is restricted to a
 * Supply Admin (processors:configure) - the admin-only surface exercised by
 * the RBAC integration test.
 */
@Tag(name = "Admin - Payment Processors", description = "Onboarding of payment processors.")
@RestController
@RequestMapping("/api/v1/payment-processors")
public class PaymentProcessorController {

    private final PaymentProcessorService paymentProcessorService;

    public PaymentProcessorController(PaymentProcessorService paymentProcessorService) {
        this.paymentProcessorService = paymentProcessorService;
    }

    @Operation(summary = "Onboard a payment processor")
    @PostMapping
    @PreAuthorize("@auth.can('processors:configure')")
    public ResponseEntity<PaymentProcessorResponse> onboard(@Valid @RequestBody CreatePaymentProcessorRequest request) {
        PaymentProcessor processor = paymentProcessorService.onboard(request.name());
        return ResponseEntity.created(URI.create("/api/v1/payment-processors/" + processor.getId()))
                .body(PaymentProcessorResponse.from(processor));
    }

    @Operation(summary = "List payment processors")
    @GetMapping
    @PreAuthorize("@auth.can('processors:read')")
    public PageResponse<PaymentProcessorResponse> list(@RequestParam(required = false) String term,
                                                         @RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                         @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return PageResponse.from(
                paymentProcessorService.list(term, PageRequest.of(page, size)).map(PaymentProcessorResponse::from));
    }

    @Operation(summary = "Get a payment processor")
    @GetMapping("/{id}")
    @PreAuthorize("@auth.can('processors:read')")
    public PaymentProcessorResponse get(@PathVariable UUID id) {
        return PaymentProcessorResponse.from(paymentProcessorService.get(id));
    }
}
