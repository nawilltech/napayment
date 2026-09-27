package ng.com.nawill.pay.onboarding.kyc;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "KYC")
@RestController
@RequestMapping("/api/v1/kyc/owner-identity")
public class OwnerIdentityController {

    private final OwnerIdentityService ownerIdentityService;

    public OwnerIdentityController(OwnerIdentityService ownerIdentityService) {
        this.ownerIdentityService = ownerIdentityService;
    }

    @Operation(summary = "Save and verify the owner's identity (BVN/NIN)")
    @PutMapping
    @PreAuthorize("@auth.can('business:kyc-manage')")
    public OwnerIdentityResponse save(@RequestBody OwnerIdentityRequest request) {
        return ownerIdentityService.save(request);
    }

    @Operation(summary = "Get the owner's identity check (BVN/NIN masked)")
    @GetMapping
    @PreAuthorize("@auth.can('business:kyc-manage')")
    public OwnerIdentityResponse get() {
        return ownerIdentityService.get();
    }
}
