package ng.com.nawill.pay.onboarding.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import ng.com.nawill.pay.common.validation.StrongPassword;

public record ResetPasswordRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "token must be a 6-digit code") String token,
        @NotBlank @StrongPassword String newPassword
) {
}
