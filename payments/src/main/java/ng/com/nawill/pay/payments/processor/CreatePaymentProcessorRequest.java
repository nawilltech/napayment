package ng.com.nawill.pay.payments.processor;

import jakarta.validation.constraints.NotBlank;

public record CreatePaymentProcessorRequest(@NotBlank String name) {
}
