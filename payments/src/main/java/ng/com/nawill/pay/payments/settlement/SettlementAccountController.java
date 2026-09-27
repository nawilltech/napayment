package ng.com.nawill.pay.payments.settlement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Settlements", description = "Split settlement configuration and settlement triggers.")
@RestController
@RequestMapping("/api/v1/settlement-accounts")
public class SettlementAccountController {

    private final SettlementAccountService settlementAccountService;

    public SettlementAccountController(SettlementAccountService settlementAccountService) {
        this.settlementAccountService = settlementAccountService;
    }

    @Operation(summary = "Attach a bank account to the virtual account with a split percentage")
    @PostMapping
    @PreAuthorize("@auth.can('settlements:manage')")
    public ResponseEntity<SettlementAccountResponse> create(@Valid @RequestBody CreateSettlementAccountRequest request) {
        SettlementAccount account = settlementAccountService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/settlement-accounts/" + account.getId()))
                .body(SettlementAccountResponse.from(account));
    }

    @Operation(summary = "List the settlement accounts and their splits")
    @GetMapping
    @PreAuthorize("@auth.can('settlements:read')")
    public PageResponse<SettlementAccountResponse> list(@RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                          @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return PageResponse.from(settlementAccountService.listForCallerVirtualAccount(PageRequest.of(page, size))
                .map(SettlementAccountResponse::from));
    }

    @Operation(summary = "Turn auto-settle after each collection on or off")
    @PatchMapping("/auto-settle")
    @PreAuthorize("@auth.can('settlements:manage')")
    public ResponseEntity<Void> toggleAutoSettle(@Valid @RequestBody AutoSettleToggleRequest request) {
        settlementAccountService.toggleAutoSettle(request);
        return ResponseEntity.noContent().build();
    }
}
