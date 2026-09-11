package ng.com.nawill.pay.payments.settlement;

import jakarta.validation.Valid;
import java.net.URI;
import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public PageResponse<SettlementAccountResponse> list(@RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                          @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return PageResponse.from(settlementAccountService.listForCallerVirtualAccount(PageRequest.of(page, size))
                .map(SettlementAccountResponse::from));
    }

    @PatchMapping("/auto-settle")
    @PreAuthorize("@auth.can('settlements:manage')")
    public ResponseEntity<Void> toggleAutoSettle(@Valid @RequestBody AutoSettleToggleRequest request) {
        settlementAccountService.toggleAutoSettle(request);
        return ResponseEntity.noContent().build();
    }
}
