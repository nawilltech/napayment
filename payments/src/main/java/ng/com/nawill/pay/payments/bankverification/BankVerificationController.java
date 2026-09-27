package ng.com.nawill.pay.payments.bankverification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import ng.com.nawill.pay.payments.bankverification.BankVerificationGateway.ResolvedAccount;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lets a caller preview the resolved account name before registering a bank account (FR-2 Name Enquiry). */
@Tag(name = "Banks", description = "Bank reference data and account Name Enquiry.")
@RestController
@RequestMapping("/api/v1/banks")
@Validated
public class BankVerificationController {

    private final BankVerificationService bankVerificationService;

    public BankVerificationController(BankVerificationService bankVerificationService) {
        this.bankVerificationService = bankVerificationService;
    }

    @Operation(summary = "Resolve a bank account's holder name (Name Enquiry)")
    @GetMapping("/resolve-account")
    @PreAuthorize("@auth.can('settlements:manage')")
    public ResolveAccountResponse resolveAccount(@RequestParam UUID bankId,
                                                  @RequestParam @NotBlank String accountNumber) {
        ResolvedAccount resolved = bankVerificationService.resolve(bankId, accountNumber);
        return new ResolveAccountResponse(resolved.accountNumber(), resolved.accountName());
    }
}
