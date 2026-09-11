package ng.com.nawill.pay.onboarding.business;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;

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

    public void updateKycDetails(String registeredName, String cacNumber, BusinessType businessType,
                                  String industry, UUID countryId, UUID stateId, String addressLine) {
        this.name = registeredName;
        this.cacNumber = cacNumber;
        this.businessType = businessType;
        this.industry = industry;
        this.countryId = countryId;
        this.stateId = stateId;
        this.addressLine = addressLine;
        this.kycDetailsUpdatedAt = Instant.now();
    }

    public void submitKycForReview() {
        this.kycStatus = KycStatus.PENDING_REVIEW;
        this.kycSubmittedAt = Instant.now();
    }
}
