package ng.com.nawill.pay.onboarding.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import ng.com.nawill.pay.common.validation.PhoneNumber;
import ng.com.nawill.pay.common.validation.StrongPassword;

public record SignupRequest(
        @NotBlank String firstName,
        String middleName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        @NotBlank @PhoneNumber String phoneNo,
        @NotBlank @StrongPassword String password,
        @NotBlank String confirmPassword,
        String businessName,
        String cacNumber
) {

    public boolean isBusinessSignup() {
        return businessName != null && !businessName.isBlank();
    }
}
