package ng.com.nawill.pay.onboarding.admin;

import java.time.Instant;
import java.util.List;
import ng.com.nawill.pay.onboarding.business.Business;
import ng.com.nawill.pay.onboarding.business.BusinessType;
import ng.com.nawill.pay.onboarding.kyc.KycDocumentResponse;
import ng.com.nawill.pay.onboarding.kyc.OwnerIdentityResponse;
import ng.com.nawill.pay.onboarding.user.User;

/**
 * Everything a reviewer needs to decide on a business's KYC: the submitted
 * details, the CAC registry lookup, the owner's identity check (BVN/NIN
 * masked), and the uploaded documents.
 */
public record AdminBusinessDetailResponse(
        AdminBusinessSummaryResponse summary,
        BusinessType businessType,
        String industry,
        String addressLine,
        String cacVerifiedName,
        String cacVerificationSource,
        Instant kycDetailsUpdatedAt,
        Instant kycReviewedAt,
        String kycReviewNote,
        String statusReason,
        Instant statusChangedAt,
        String ownerPhoneNo,
        OwnerIdentityResponse ownerIdentity,
        List<KycDocumentResponse> documents
) {

    public static AdminBusinessDetailResponse from(Business business, User owner, OwnerIdentityResponse ownerIdentity,
                                                   List<KycDocumentResponse> documents) {
        return new AdminBusinessDetailResponse(AdminBusinessSummaryResponse.from(business, owner),
                business.getBusinessType(), business.getIndustry(), business.getAddressLine(),
                business.getCacVerifiedName(), business.getCacVerificationSource(), business.getKycDetailsUpdatedAt(),
                business.getKycReviewedAt(), business.getKycReviewNote(), business.getStatusReason(),
                business.getStatusChangedAt(), owner == null ? null : owner.getPhoneNo(),
                ownerIdentity, documents);
    }
}
