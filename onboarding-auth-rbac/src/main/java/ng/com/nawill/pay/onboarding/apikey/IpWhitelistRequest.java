package ng.com.nawill.pay.onboarding.apikey;

import jakarta.validation.constraints.NotBlank;

public record IpWhitelistRequest(@NotBlank String cidr) {
}
