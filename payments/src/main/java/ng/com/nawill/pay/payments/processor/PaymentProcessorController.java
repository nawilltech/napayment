package ng.com.nawill.pay.payments.processor;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Onboarding/activation of payment processors (FR-6) is restricted to a
 * Supply Admin (processors:configure) - the admin-only surface exercised by
 * the RBAC integration test.
 */
@RestController
@RequestMapping("/api/v1/payment-processors")
public class PaymentProcessorController {

    private final PaymentProcessorService paymentProcessorService;

    public PaymentProcessorController(PaymentProcessorService paymentProcessorService) {
        this.paymentProcessorService = paymentProcessorService;
    }

    @PostMapping
    @PreAuthorize("@auth.can('processors:configure')")
    public ResponseEntity<PaymentProcessorResponse> onboard(@Valid @RequestBody CreatePaymentProcessorRequest request) {
        PaymentProcessor processor = paymentProcessorService.onboard(request.name());
        return ResponseEntity.created(URI.create("/api/v1/payment-processors/" + processor.getId()))
                .body(PaymentProcessorResponse.from(processor));
    }

    @GetMapping
    @PreAuthorize("@auth.can('processors:read')")
    public List<PaymentProcessorResponse> list() {
        return paymentProcessorService.list().stream().map(PaymentProcessorResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@auth.can('processors:read')")
    public PaymentProcessorResponse get(@PathVariable UUID id) {
        return PaymentProcessorResponse.from(paymentProcessorService.get(id));
    }
}
