package ng.com.nawill.pay.payments.bankaccount;

import jakarta.validation.Valid;
import java.net.URI;
import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A business's registered real bank accounts, later attached to a settlement config (FR-2). */
@RestController
@RequestMapping("/api/v1/bank-accounts")
public class BankAccountController {

    private final BankAccountService bankAccountService;

    public BankAccountController(BankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    @PostMapping
    @PreAuthorize("@auth.can('settlements:manage')")
    public ResponseEntity<BankAccountResponse> create(@Valid @RequestBody CreateBankAccountRequest request) {
        BankAccount bankAccount = bankAccountService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/bank-accounts/" + bankAccount.getId()))
                .body(BankAccountResponse.from(bankAccount));
    }

    @GetMapping
    @PreAuthorize("@auth.can('settlements:read')")
    public PageResponse<BankAccountResponse> list(@RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                    @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return PageResponse.from(
                bankAccountService.listForCallerBusiness(PageRequest.of(page, size)).map(BankAccountResponse::from));
    }
}
