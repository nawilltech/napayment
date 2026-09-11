package ng.com.nawill.pay.onboarding.business;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business/contact")
public class BusinessContactController {

    private final BusinessContactService businessContactService;

    public BusinessContactController(BusinessContactService businessContactService) {
        this.businessContactService = businessContactService;
    }

    @PutMapping
    @PreAuthorize("@auth.can('business:manage')")
    public BusinessContactResponse update(@Valid @RequestBody BusinessContactRequest request) {
        return businessContactService.update(request);
    }

    @GetMapping
    @PreAuthorize("@auth.can('business:manage')")
    public BusinessContactResponse get() {
        return businessContactService.get();
    }
}
