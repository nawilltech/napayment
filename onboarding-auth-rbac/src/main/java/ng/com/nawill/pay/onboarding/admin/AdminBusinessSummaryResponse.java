package ng.com.nawill.pay.onboarding.admin;

import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.onboarding.business.Business;
import ng.com.nawill.pay.onboarding.business.KycStatus;
import ng.com.nawill.pay.onboarding.user.User;

/** One row of the platform business list. {@code owner} may be null if the owner row is gone. */
public record AdminBusinessSummaryResponse(
        UUID id,
        String name,
        String cacNumber,
        boolean cacVerified,
        KycStatus kycStatus,
        Instant kycSubmittedAt,
        String ownerName,
        String ownerEmail,
        Instant createdAt
) {

    public static AdminBusinessSummaryResponse from(Business business, User owner) {
        return new AdminBusinessSummaryResponse(business.getId(), business.getName(), business.getCacNumber(),
                business.isCacVerified(), business.getKycStatus(), business.getKycSubmittedAt(),
                owner == null ? null : owner.getFirstName() + " " + owner.getLastName(),
                owner == null ? null : owner.getEmail(), business.getCreatedAt());
    }
}
