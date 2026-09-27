package ng.com.nawill.pay.payments.paymentlink;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import ng.com.nawill.pay.common.idempotency.Idempotent;
import ng.com.nawill.pay.common.idempotency.IdempotencyConstants;
import ng.com.nawill.pay.payments.transaction.Transaction;
import ng.com.nawill.pay.payments.transaction.TransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated payer-facing surface (permitAll in SecurityConfig)
 * - the payer is a random third party with no Nawill Pay account, so this
 * is deliberately a separate controller from the business-facing,
 * authenticated {@code PaymentLinkController} (clear separation of
 * concerns: who's allowed to call each is fundamentally different).
 */
@Tag(name = "Payment Links - Payer", description = "Payer-facing payment link pages; no account needed.")
@RestController
@RequestMapping("/api/v1/pay")
public class PaymentLinkPayController {

    private final PaymentLinkService paymentLinkService;

    public PaymentLinkPayController(PaymentLinkService paymentLinkService) {
        this.paymentLinkService = paymentLinkService;
    }

    @Operation(summary = "Get a payment link's details by short code")
    @GetMapping("/{shortCode}")
    public PaymentLinkCheckoutResponse resolve(@PathVariable String shortCode) {
        return paymentLinkService.checkout(shortCode);
    }

    @Operation(summary = "Pay through a payment link")
    @PostMapping("/{shortCode}")
    @Idempotent
    public ResponseEntity<TransactionResponse> pay(@PathVariable String shortCode,
                                                     @Valid @RequestBody PayLinkRequest request,
                                                     HttpServletRequest httpRequest) {
        String idempotencyKey = httpRequest.getHeader(IdempotencyConstants.HEADER);
        Transaction transaction = paymentLinkService.pay(shortCode, request, idempotencyKey);
        return ResponseEntity.ok(TransactionResponse.from(transaction));
    }
}
