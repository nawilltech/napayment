package ng.com.nawill.pay.onboarding.business;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "KYC", description = "Business KYC onboarding.")
@RestController
@RequestMapping("/api/v1/business/kyc/details")
public class BusinessKycController {

    private final BusinessKycService businessKycService;

    public BusinessKycController(BusinessKycService businessKycService) {
        this.businessKycService = businessKycService;
    }

    @Operation(summary = "Save the business's KYC details")
    @PutMapping
    @PreAuthorize("@auth.can('business:kyc-manage')")
    public BusinessKycDetailsResponse update(@Valid @RequestBody BusinessKycDetailsRequest request) {
        return BusinessKycDetailsResponse.from(businessKycService.updateDetails(request));
    }

    @Operation(summary = "Get the business's KYC details")
    @GetMapping
    @PreAuthorize("@auth.can('business:kyc-manage')")
    public BusinessKycDetailsResponse get() {
        Business business = businessKycService.getDetails();
        return business.getKycDetailsUpdatedAt() == null ? null : BusinessKycDetailsResponse.from(business);
    }
}
