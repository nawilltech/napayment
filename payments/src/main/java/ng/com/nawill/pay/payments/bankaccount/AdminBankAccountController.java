package ng.com.nawill.pay.payments.bankaccount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform admin console (FR-Admin-4): settlement bank accounts of any
 * business. Same Name Enquiry and duplicate rules as the business-scoped
 * {@link BankAccountController}; {@code created_by} records the admin.
 */
@Tag(name = "Admin - Bank Accounts", description = "Settlement bank accounts of any business.")
@RestController
@RequestMapping("/api/v1/admin/businesses/{businessId}/bank-accounts")
public class AdminBankAccountController {

    private final BankAccountService bankAccountService;

    public AdminBankAccountController(BankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    @Operation(summary = "Register a settlement bank account for a business (name resolved via Name Enquiry)")
    @PostMapping
    @PreAuthorize("@auth.can('platform-bank-accounts:manage')")
    public ResponseEntity<BankAccountResponse> create(@PathVariable UUID businessId,
                                                      @Valid @RequestBody CreateBankAccountRequest request) {
        BankAccount bankAccount = bankAccountService.createOnBehalfOf(businessId, request);
        return ResponseEntity.created(URI.create("/api/v1/bank-accounts/" + bankAccount.getId()))
                .body(BankAccountResponse.from(bankAccount));
    }

    @Operation(summary = "List a business's settlement bank accounts")
    @GetMapping
    @PreAuthorize("@auth.can('platform-businesses:read')")
    public PageResponse<BankAccountResponse> list(@PathVariable UUID businessId,
                                                  @RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                  @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return PageResponse.from(
                bankAccountService.listForBusiness(businessId, PageRequest.of(page, size)).map(BankAccountResponse::from));
    }
}
