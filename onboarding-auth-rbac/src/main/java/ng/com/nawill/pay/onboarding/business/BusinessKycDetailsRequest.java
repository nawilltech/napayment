package ng.com.nawill.pay.onboarding.business;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record BusinessKycDetailsRequest(
        @NotNull @Size(min = 2, max = 128) String registeredName,
        @NotNull @Pattern(regexp = "(?i)^(RC|BN|IT)\\d{4,10}$", message = "Format like RC1234567") String cacNumber,
        @NotNull BusinessType businessType,
        @NotNull @Size(min = 1, max = 128) String industry,
        @NotNull UUID countryId,
        @NotNull UUID stateId,
        @NotNull @Size(min = 3, max = 256) String addressLine
) {
}
