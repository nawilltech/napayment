package ng.com.nawill.pay.onboarding.kyc;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kyc/submit")
public class KycSubmitController {

    private final KycSubmitService kycSubmitService;

    public KycSubmitController(KycSubmitService kycSubmitService) {
        this.kycSubmitService = kycSubmitService;
    }

    @PostMapping
    @PreAuthorize("@auth.can('business:kyc-manage')")
    public KycSubmitResponse submit() {
        return kycSubmitService.submit();
    }
}
