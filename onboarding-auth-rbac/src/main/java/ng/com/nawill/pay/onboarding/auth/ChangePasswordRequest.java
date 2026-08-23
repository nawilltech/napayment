package ng.com.nawill.pay.onboarding.auth;

import jakarta.validation.constraints.NotBlank;
import ng.com.nawill.pay.common.validation.StrongPassword;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @StrongPassword String newPassword
) {
}
