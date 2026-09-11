package ng.com.nawill.pay.onboarding.business;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BusinessContactRequest(
        List<@Email String> disputeEmails,
        List<@Email String> refundEmails,
        @Email String supportEmail,
        @NotNull @Email String generalEmail
) {
}
