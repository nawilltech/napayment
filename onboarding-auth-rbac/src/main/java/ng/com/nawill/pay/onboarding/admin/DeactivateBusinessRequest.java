package ng.com.nawill.pay.onboarding.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Shown to platform staff and recorded in the audit log (FR-Admin-6). */
public record DeactivateBusinessRequest(@NotBlank @Size(max = 512) String reason) {
}
