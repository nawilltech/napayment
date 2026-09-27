package ng.com.nawill.pay.payments.thirdparty;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import ng.com.nawill.pay.common.idempotency.Idempotent;
import ng.com.nawill.pay.common.idempotency.IdempotencyConstants;
import ng.com.nawill.pay.payments.settlement.Settlement;
import ng.com.nawill.pay.payments.settlement.SettlementResponse;
import ng.com.nawill.pay.payments.settlement.SettlementService;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccount;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccountQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Third-party withdrawal via a business's API key (FR-9, doc 3 §2.5) - the
 * API-key-authenticated equivalent of the JWT-authenticated manual
 * settlement trigger; both delegate to the same {@link SettlementService}.
 */
@Tag(name = "Server-to-Server (API Key)")
@RestController
@RequestMapping("/api/v1/withdraw")
public class WithdrawController {

    private final SettlementService settlementService;
    private final VirtualAccountQueryService virtualAccountQueryService;

    public WithdrawController(SettlementService settlementService, VirtualAccountQueryService virtualAccountQueryService) {
        this.settlementService = settlementService;
        this.virtualAccountQueryService = virtualAccountQueryService;
    }

    @Operation(summary = "Settle the virtual account balance to its settlement accounts")
    @PostMapping
    @Idempotent
    @PreAuthorize("@auth.can('withdraw:create')")
    public ResponseEntity<List<SettlementResponse>> withdraw(@Valid @RequestBody WithdrawRequest request,
                                                               HttpServletRequest httpRequest) {
        String idempotencyKey = httpRequest.getHeader(IdempotencyConstants.HEADER);
        VirtualAccount virtualAccount = virtualAccountQueryService.requireSoleVirtualAccountForCaller();
        List<Settlement> settlements = settlementService.settle(virtualAccount.getId(), request.amount(), idempotencyKey);
        return ResponseEntity.ok(settlements.stream().map(SettlementResponse::from).toList());
    }
}
