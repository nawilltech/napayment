package ng.com.nawill.pay.onboarding.team;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInviteRequest(
        @NotNull @Email String email,
        @NotNull RoleTemplate roleTemplate,
        @Size(max = 280) String message
) {
}
