package ng.com.nawill.pay.onboarding.kyc;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "KYC")
@RestController
@RequestMapping("/api/v1/kyc/submit")
public class KycSubmitController {

    private final KycSubmitService kycSubmitService;

    public KycSubmitController(KycSubmitService kycSubmitService) {
        this.kycSubmitService = kycSubmitService;
    }

    @Operation(summary = "Submit KYC for platform review")
    @PostMapping
    @PreAuthorize("@auth.can('business:kyc-manage')")
    public KycSubmitResponse submit() {
        return kycSubmitService.submit();
    }
}
