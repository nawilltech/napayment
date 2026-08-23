package ng.com.nawill.pay.payments.settlement;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import ng.com.nawill.pay.common.idempotency.Idempotent;
import ng.com.nawill.pay.common.idempotency.IdempotencyConstants;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccount;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccountQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manual, JWT-authenticated settlement trigger - "settle on request" (FR-Settle-1). */
@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private final SettlementService settlementService;
    private final VirtualAccountQueryService virtualAccountQueryService;

    public SettlementController(SettlementService settlementService, VirtualAccountQueryService virtualAccountQueryService) {
        this.settlementService = settlementService;
        this.virtualAccountQueryService = virtualAccountQueryService;
    }

    @PostMapping
    @Idempotent
    @PreAuthorize("@auth.can('settlements:manage')")
    public ResponseEntity<List<SettlementResponse>> settle(@Valid @RequestBody SettleRequest request,
                                                             HttpServletRequest httpRequest) {
        String idempotencyKey = httpRequest.getHeader(IdempotencyConstants.HEADER);
        VirtualAccount virtualAccount = virtualAccountQueryService.requireSoleVirtualAccountForCaller();
        List<Settlement> settlements = settlementService.settle(virtualAccount.getId(), request.amount(), idempotencyKey);
        return ResponseEntity.ok(settlements.stream().map(SettlementResponse::from).toList());
    }
}
