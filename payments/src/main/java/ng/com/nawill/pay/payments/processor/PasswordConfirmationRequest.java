package ng.com.nawill.pay.payments.processor;

import jakarta.validation.constraints.NotBlank;

/** The signed-in staff member's own password, re-entered to confirm a platform-wide change (FR-Admin-5). */
public record PasswordConfirmationRequest(@NotBlank String password) {
}
