package ng.com.nawill.pay.payments.settlement;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlement-accounts")
public class SettlementAccountController {

    private final SettlementAccountService settlementAccountService;

    public SettlementAccountController(SettlementAccountService settlementAccountService) {
        this.settlementAccountService = settlementAccountService;
    }

    @PostMapping
    @PreAuthorize("@auth.can('settlements:manage')")
    public ResponseEntity<SettlementAccountResponse> create(@Valid @RequestBody CreateSettlementAccountRequest request) {
        SettlementAccount account = settlementAccountService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/settlement-accounts/" + account.getId()))
                .body(SettlementAccountResponse.from(account));
    }

    @GetMapping
    @PreAuthorize("@auth.can('settlements:read')")
    public List<SettlementAccountResponse> list() {
        return settlementAccountService.listForCallerVirtualAccount().stream().map(SettlementAccountResponse::from).toList();
    }

    @PatchMapping("/auto-settle")
    @PreAuthorize("@auth.can('settlements:manage')")
    public ResponseEntity<Void> toggleAutoSettle(@Valid @RequestBody AutoSettleToggleRequest request) {
        settlementAccountService.toggleAutoSettle(request);
        return ResponseEntity.noContent().build();
    }
}
