package ng.com.nawill.pay.onboarding.business;

import java.time.Instant;
import java.util.UUID;

public record BusinessKycDetailsResponse(
        String registeredName,
        String cacNumber,
        BusinessType businessType,
        String industry,
        UUID countryId,
        UUID stateId,
        String addressLine,
        Instant updatedAt
) {

    public static BusinessKycDetailsResponse from(Business business) {
        return new BusinessKycDetailsResponse(
                business.getName(),
                business.getCacNumber(),
                business.getBusinessType(),
                business.getIndustry(),
                business.getCountryId(),
                business.getStateId(),
                business.getAddressLine(),
                business.getKycDetailsUpdatedAt());
    }
}
