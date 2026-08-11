package ng.com.nawill.pay.onboarding.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank String firstName,
        String middleName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        @NotBlank String phoneNo,
        @NotBlank @Size(min = 8) String password,
        String businessName,
        String cacNumber
) {

    public boolean isBusinessSignup() {
        return businessName != null && !businessName.isBlank();
    }
}
