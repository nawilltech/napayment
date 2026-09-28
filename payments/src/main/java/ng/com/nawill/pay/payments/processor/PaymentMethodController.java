package ng.com.nawill.pay.payments.processor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What the caller's account can currently accept (FR-Proc-3), e.g. for a checkout screen. */
@Tag(name = "Payment Methods", description = "Payment methods available to the caller's account.")
@RestController
@RequestMapping("/api/v1/payment-methods")
public class PaymentMethodController {

    private final ProcessorRouter processorRouter;
    private final CurrentUserResolver currentUserResolver;

    public PaymentMethodController(ProcessorRouter processorRouter, CurrentUserResolver currentUserResolver) {
        this.processorRouter = processorRouter;
        this.currentUserResolver = currentUserResolver;
    }

    @Operation(summary = "List the payment methods the caller's account can accept")
    @GetMapping
    public List<PaymentMethodOption> available() {
        return processorRouter.availableMethods(currentUserResolver.requireCurrentUser().businessId()).stream()
                .map(PaymentMethodOption::of)
                .toList();
    }
}
