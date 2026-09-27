package ng.com.nawill.pay.payments.virtualaccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Virtual Accounts", description = "The caller's virtual accounts.")
@RestController
@RequestMapping("/api/v1/virtual-accounts")
public class VirtualAccountController {

    private final VirtualAccountQueryService virtualAccountQueryService;

    public VirtualAccountController(VirtualAccountQueryService virtualAccountQueryService) {
        this.virtualAccountQueryService = virtualAccountQueryService;
    }

    @Operation(summary = "List the caller's virtual accounts and balances")
    @GetMapping
    @PreAuthorize("@auth.can('virtualaccounts:read')")
    public PageResponse<VirtualAccountResponse> listMine(@RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                          @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return PageResponse.from(
                virtualAccountQueryService.listForCaller(PageRequest.of(page, size)).map(VirtualAccountResponse::from));
    }
}
