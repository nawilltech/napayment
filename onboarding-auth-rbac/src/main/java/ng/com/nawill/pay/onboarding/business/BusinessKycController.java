package ng.com.nawill.pay.onboarding.business;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business/kyc/details")
public class BusinessKycController {

    private final BusinessKycService businessKycService;

    public BusinessKycController(BusinessKycService businessKycService) {
        this.businessKycService = businessKycService;
    }

    @PutMapping
    @PreAuthorize("@auth.can('business:kyc-manage')")
    public BusinessKycDetailsResponse update(@Valid @RequestBody BusinessKycDetailsRequest request) {
        return BusinessKycDetailsResponse.from(businessKycService.updateDetails(request));
    }

    @GetMapping
    @PreAuthorize("@auth.can('business:kyc-manage')")
    public BusinessKycDetailsResponse get() {
        Business business = businessKycService.getDetails();
        return business.getKycDetailsUpdatedAt() == null ? null : BusinessKycDetailsResponse.from(business);
    }
}
