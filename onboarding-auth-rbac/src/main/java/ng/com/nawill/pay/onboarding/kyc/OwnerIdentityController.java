package ng.com.nawill.pay.onboarding.kyc;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kyc/owner-identity")
public class OwnerIdentityController {

    private final OwnerIdentityService ownerIdentityService;

    public OwnerIdentityController(OwnerIdentityService ownerIdentityService) {
        this.ownerIdentityService = ownerIdentityService;
    }

    @PutMapping
    @PreAuthorize("@auth.can('business:kyc-manage')")
    public OwnerIdentityResponse save(@RequestBody OwnerIdentityRequest request) {
        return ownerIdentityService.save(request);
    }

    @GetMapping
    @PreAuthorize("@auth.can('business:kyc-manage')")
    public OwnerIdentityResponse get() {
        return ownerIdentityService.get();
    }
}
