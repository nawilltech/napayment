package ng.com.nawill.pay.onboarding.business;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;
import ng.com.nawill.pay.common.entity.EntityStatus;

@Entity
@Table(name = "business")
public class Business extends BaseEntity {

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "cac_number", length = 32)
    private String cacNumber;

    @Column(name = "address_id")
    private UUID addressId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", length = 32)
    private BusinessType businessType;

    @Column(name = "industry", length = 128)
    private String industry;

    @Column(name = "country_id")
    private UUID countryId;

    @Column(name = "state_id")
    private UUID stateId;

    @Column(name = "address_line", length = 256)
    private String addressLine;

    @Column(name = "kyc_details_updated_at")
    private Instant kycDetailsUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 16)
    private KycStatus kycStatus = KycStatus.NOT_STARTED;

    @Column(name = "kyc_submitted_at")
    private Instant kycSubmittedAt;

    @Column(name = "cac_verified", nullable = false)
    private boolean cacVerified = false;

    @Column(name = "cac_verified_name", length = 256)
    private String cacVerifiedName;

    @Column(name = "cac_verification_source", length = 32)
    private String cacVerificationSource;

    @Column(name = "kyc_reviewed_at")
    private Instant kycReviewedAt;

    @Column(name = "kyc_reviewed_by")
    private UUID kycReviewedBy;

    @Column(name = "kyc_review_note", length = 512)
    private String kycReviewNote;

    @Column(name = "status_reason", length = 512)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "status_changed_by")
    private UUID statusChangedBy;

    protected Business() {
    }

    public Business(String name, String cacNumber, UUID ownerId) {
        this.name = name;
        this.cacNumber = cacNumber;
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }

    public String getCacNumber() {
        return cacNumber;
    }

    public UUID getAddressId() {
        return addressId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public BusinessType getBusinessType() {
        return businessType;
    }

    public String getIndustry() {
        return industry;
    }

    public UUID getCountryId() {
        return countryId;
    }

    public UUID getStateId() {
        return stateId;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public Instant getKycDetailsUpdatedAt() {
        return kycDetailsUpdatedAt;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public Instant getKycSubmittedAt() {
        return kycSubmittedAt;
    }

    public boolean isCacVerified() {
        return cacVerified;
    }

    public String getCacVerifiedName() {
        return cacVerifiedName;
    }

    public String getCacVerificationSource() {
        return cacVerificationSource;
    }

    public Instant getKycReviewedAt() {
        return kycReviewedAt;
    }

    public UUID getKycReviewedBy() {
        return kycReviewedBy;
    }

    public String getKycReviewNote() {
        return kycReviewNote;
    }

    public void updateKycDetails(String registeredName, String cacNumber, BusinessType businessType,
                                  String industry, UUID countryId, UUID stateId, String addressLine,
                                  CacLookupResult cacLookupResult) {
        this.name = registeredName;
        this.cacNumber = cacNumber;
        this.businessType = businessType;
        this.industry = industry;
        this.countryId = countryId;
        this.stateId = stateId;
        this.addressLine = addressLine;
        this.kycDetailsUpdatedAt = Instant.now();
        this.cacVerified = cacLookupResult.verified();
        this.cacVerifiedName = cacLookupResult.matchedName();
        this.cacVerificationSource = cacLookupResult.source();
    }

    public void submitKycForReview() {
        this.kycStatus = KycStatus.PENDING_REVIEW;
        this.kycSubmittedAt = Instant.now();
        // A resubmission starts a fresh review - the previous decision no longer applies.
        this.kycReviewedAt = null;
        this.kycReviewedBy = null;
        this.kycReviewNote = null;
    }

    /** FR-3 platform review outcome. Callers check {@link #isAwaitingKycReview()} first. */
    public void recordKycDecision(KycStatus decision, UUID reviewerId, String note) {
        if (decision != KycStatus.VERIFIED && decision != KycStatus.REJECTED) {
            throw new IllegalArgumentException("A KYC decision is VERIFIED or REJECTED, not " + decision);
        }
        this.kycStatus = decision;
        this.kycReviewedAt = Instant.now();
        this.kycReviewedBy = reviewerId;
        this.kycReviewNote = note;
    }

    public boolean isAwaitingKycReview() {
        return kycStatus == KycStatus.PENDING_REVIEW;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public boolean isActive() {
        return getStatus() == EntityStatus.ACTIVE;
    }

    /** FR-Admin-6: stops all money movement for the business; its users keep read-only access. */
    public void deactivate(String reason, UUID by) {
        setStatus(EntityStatus.INACTIVE);
        this.statusReason = reason;
        this.statusChangedAt = Instant.now();
        this.statusChangedBy = by;
    }

    public void activate(UUID by) {
        setStatus(EntityStatus.ACTIVE);
        this.statusReason = null;
        this.statusChangedAt = Instant.now();
        this.statusChangedBy = by;
    }
}
