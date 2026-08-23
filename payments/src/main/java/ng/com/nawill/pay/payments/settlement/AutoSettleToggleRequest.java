package ng.com.nawill.pay.payments.settlement;

import jakarta.validation.constraints.NotNull;

public record AutoSettleToggleRequest(@NotNull Boolean autoSettle) {
}
