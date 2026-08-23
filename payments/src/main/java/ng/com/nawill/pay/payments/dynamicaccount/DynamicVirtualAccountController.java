package ng.com.nawill.pay.payments.dynamicaccount;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import ng.com.nawill.pay.common.idempotency.Idempotent;
import ng.com.nawill.pay.common.idempotency.IdempotencyConstants;
import ng.com.nawill.pay.payments.transaction.Transaction;
import ng.com.nawill.pay.payments.transaction.TransactionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/temporary-accounts")
public class DynamicVirtualAccountController {

    private final DynamicVirtualAccountService dynamicVirtualAccountService;

    public DynamicVirtualAccountController(DynamicVirtualAccountService dynamicVirtualAccountService) {
        this.dynamicVirtualAccountService = dynamicVirtualAccountService;
    }

    @PostMapping
    @PreAuthorize("@auth.can('temporaryaccounts:manage')")
    public ResponseEntity<DynamicVirtualAccountResponse> mint(@Valid @RequestBody CreateDynamicAccountRequest request) {
        DynamicVirtualAccount account = dynamicVirtualAccountService.mint(request);
        return ResponseEntity.created(URI.create("/api/v1/temporary-accounts/" + account.getId()))
                .body(DynamicVirtualAccountResponse.from(account));
    }

    @GetMapping
    @PreAuthorize("@auth.can('temporaryaccounts:manage')")
    public List<DynamicVirtualAccountResponse> list() {
        return dynamicVirtualAccountService.listForCallerBusiness().stream()
                .map(DynamicVirtualAccountResponse::from).toList();
    }

    /**
     * Sandboxed stand-in for a real NIBSS inbound-transfer webhook (same
     * "scoped, not really implemented" posture as everything else in this
     * MVP) - authenticated, not public, since only the owning business
     * should be able to simulate a payment landing on its own account.
     */
    @PostMapping("/{accountNumber}/simulate-deposit")
    @Idempotent
    @PreAuthorize("@auth.can('temporaryaccounts:manage')")
    public ResponseEntity<TransactionResponse> simulateDeposit(@PathVariable String accountNumber,
                                                                 @Valid @RequestBody SimulateDepositRequest request,
                                                                 HttpServletRequest httpRequest) {
        String idempotencyKey = httpRequest.getHeader(IdempotencyConstants.HEADER);
        Transaction transaction = dynamicVirtualAccountService.simulateDeposit(accountNumber, request, idempotencyKey);
        return ResponseEntity.ok(TransactionResponse.from(transaction));
    }
}
