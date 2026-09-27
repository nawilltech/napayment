package ng.com.nawill.pay.payments.processor;

import jakarta.validation.constraints.NotNull;

public record SetBusinessPaymentProcessorRequest(@NotNull Boolean enabled) {
}
