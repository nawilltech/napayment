package ng.com.nawill.pay.payments.paymentlink;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import ng.com.nawill.pay.common.idempotency.Idempotent;
import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Business-facing, JWT-authenticated payment link management (FR-14). */
@RestController
@RequestMapping("/api/v1/payment-links")
public class PaymentLinkController {

    private final PaymentLinkService paymentLinkService;

    public PaymentLinkController(PaymentLinkService paymentLinkService) {
        this.paymentLinkService = paymentLinkService;
    }

    @PostMapping
    @Idempotent
    @PreAuthorize("@auth.can('paymentlinks:manage')")
    public ResponseEntity<PaymentLinkResponse> create(@Valid @RequestBody CreatePaymentLinkRequest request) {
        PaymentLink link = paymentLinkService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/pay/" + link.getShortCode()))
                .body(PaymentLinkResponse.from(link));
    }

    @GetMapping
    @PreAuthorize("@auth.can('paymentlinks:read')")
    public PageResponse<PaymentLinkResponse> list(@RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                    @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return PageResponse.from(
                paymentLinkService.listForCallerBusiness(PageRequest.of(page, size)).map(PaymentLinkResponse::from));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@auth.can('paymentlinks:manage')")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        paymentLinkService.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
