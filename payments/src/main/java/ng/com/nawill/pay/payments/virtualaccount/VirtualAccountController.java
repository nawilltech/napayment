package ng.com.nawill.pay.payments.virtualaccount;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public List<VirtualAccountResponse> listMine() {
        return virtualAccountQueryService.listForCaller().stream().map(VirtualAccountResponse::from).toList();
    }
}
