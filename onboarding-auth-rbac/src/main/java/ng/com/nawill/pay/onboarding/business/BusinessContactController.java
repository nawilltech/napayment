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

@Tag(name = "Business Settings", description = "The business's contact settings.")
@RestController
@RequestMapping("/api/v1/business/contact")
public class BusinessContactController {

    private final BusinessContactService businessContactService;

    public BusinessContactController(BusinessContactService businessContactService) {
        this.businessContactService = businessContactService;
    }

    @Operation(summary = "Update the business's contact settings")
    @PutMapping
    @PreAuthorize("@auth.can('business:manage')")
    public BusinessContactResponse update(@Valid @RequestBody BusinessContactRequest request) {
        return businessContactService.update(request);
    }

    @Operation(summary = "Get the business's contact settings")
    @GetMapping
    @PreAuthorize("@auth.can('business:manage')")
    public BusinessContactResponse get() {
        return businessContactService.get();
    }
}
