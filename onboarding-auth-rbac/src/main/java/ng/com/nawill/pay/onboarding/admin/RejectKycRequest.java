package ng.com.nawill.pay.onboarding.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The reason is shown back to the business so they know what to fix before resubmitting. */
public record RejectKycRequest(@NotBlank @Size(max = 512) String reason) {
}
