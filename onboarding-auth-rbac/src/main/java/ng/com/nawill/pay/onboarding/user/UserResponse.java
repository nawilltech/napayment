package ng.com.nawill.pay.onboarding.user;

import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.onboarding.business.Business;

public record UserResponse(
        UUID userId,
        UUID businessId,
        String firstName,
        String middleName,
        String lastName,
        String email,
        String phoneNo,
        UserType userType,
        String businessName,
        String cacNumber,
        /** False when platform staff have deactivated the business (FR-Admin-6): read-only until reactivated. */
        boolean businessActive,
        boolean isVerified,
        Instant createdAt
) {

    public static UserResponse from(User user, Business business) {
        return new UserResponse(
                user.getId(),
                user.getBusinessId(),
                user.getFirstName(),
                user.getMiddleName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNo(),
                user.getUserType(),
                business == null ? null : business.getName(),
                business == null ? null : business.getCacNumber(),
                business == null || business.isActive(),
                user.isVerified(),
                user.getCreatedAt());
    }
}
