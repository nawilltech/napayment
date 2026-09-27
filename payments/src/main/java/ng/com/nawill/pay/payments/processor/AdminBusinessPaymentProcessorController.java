package ng.com.nawill.pay.payments.processor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Processor settings for one business, from its page in the admin console (FR-Admin-5). */
@Tag(name = "Admin - Payment Processors")
@RestController
@RequestMapping("/api/v1/admin/businesses/{businessId}/payment-processors")
public class AdminBusinessPaymentProcessorController {

    private final BusinessPaymentProcessorService service;

    public AdminBusinessPaymentProcessorController(BusinessPaymentProcessorService service) {
        this.service = service;
    }

    @Operation(summary = "Every processor with whether it's available to this business, and why")
    @GetMapping
    @PreAuthorize("@auth.can('platform-processors:read')")
    public List<BusinessPaymentProcessorResponse> list(@PathVariable UUID businessId) {
        return service.list(businessId);
    }

    @Operation(summary = "Switch a processor ON or OFF for this business")
    @PutMapping("/{processorId}")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public BusinessPaymentProcessorResponse set(@PathVariable UUID businessId, @PathVariable UUID processorId,
                                                @Valid @RequestBody SetBusinessPaymentProcessorRequest request) {
        return service.set(businessId, processorId, request.enabled());
    }

    @Operation(summary = "Reset this business to follow the processor's platform default")
    @DeleteMapping("/{processorId}")
    @PreAuthorize("@auth.can('platform-processors:manage')")
    public BusinessPaymentProcessorResponse reset(@PathVariable UUID businessId, @PathVariable UUID processorId) {
        return service.reset(businessId, processorId);
    }
}
