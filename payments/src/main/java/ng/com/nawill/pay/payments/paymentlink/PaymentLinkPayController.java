package ng.com.nawill.pay.payments.paymentlink;

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
@RestController
@RequestMapping("/api/v1/pay")
public class PaymentLinkPayController {

    private final PaymentLinkService paymentLinkService;

    public PaymentLinkPayController(PaymentLinkService paymentLinkService) {
        this.paymentLinkService = paymentLinkService;
    }

    @GetMapping("/{shortCode}")
    public PaymentLinkResponse resolve(@PathVariable String shortCode) {
        return PaymentLinkResponse.from(paymentLinkService.resolve(shortCode));
    }

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
