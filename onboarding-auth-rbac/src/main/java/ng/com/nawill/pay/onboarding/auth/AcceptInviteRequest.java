package ng.com.nawill.pay.onboarding.auth;

import jakarta.validation.constraints.NotBlank;
import ng.com.nawill.pay.common.validation.StrongPassword;

public record AcceptInviteRequest(
        @NotBlank String token,
        @NotBlank String firstName,
        String middleName,
        @NotBlank String lastName,
        @NotBlank String phoneNo,
        @NotBlank @StrongPassword String password
) {
}
