package ng.com.nawill.pay.payments.virtualaccount;

import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/virtual-accounts")
public class VirtualAccountController {

    private final VirtualAccountQueryService virtualAccountQueryService;

    public VirtualAccountController(VirtualAccountQueryService virtualAccountQueryService) {
        this.virtualAccountQueryService = virtualAccountQueryService;
    }

    @GetMapping
    @PreAuthorize("@auth.can('virtualaccounts:read')")
    public PageResponse<VirtualAccountResponse> listMine(@RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                          @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return PageResponse.from(
                virtualAccountQueryService.listForCaller(PageRequest.of(page, size)).map(VirtualAccountResponse::from));
    }
}
