package ng.com.nawill.pay.onboarding.kyc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;

/**
 * FR-8: the business owner's BVN/NIN, one row per business. bvn/nin are
 * stored AES-256-GCM encrypted (never plaintext) via {@code
 * EncryptionService} - same posture as {@code ApiKeyCredential.secretKeyEncrypted}.
 * {@code verified} is set only by {@link IdentityVerificationGateway}, never
 * directly from caller input.
 */
@Entity
@Table(name = "owner_identities")
public class OwnerIdentity extends BaseEntity {

    @Column(name = "business_id", nullable = false, unique = true)
    private UUID businessId;

    @Column(name = "bvn_encrypted", columnDefinition = "text")
    private String bvnEncrypted;

    @Column(name = "nin_encrypted", columnDefinition = "text")
    private String ninEncrypted;

    @Column(name = "verified", nullable = false)
    private boolean verified = false;

    @Column(name = "provider_reference", length = 64)
    private String providerReference;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected OwnerIdentity() {
    }

    public OwnerIdentity(UUID businessId) {
        this.businessId = businessId;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getBvnEncrypted() {
        return bvnEncrypted;
    }

    public String getNinEncrypted() {
        return ninEncrypted;
    }

    public boolean isVerified() {
        return verified;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void apply(String bvnEncrypted, String ninEncrypted, VerificationResult result) {
        this.bvnEncrypted = bvnEncrypted;
        this.ninEncrypted = ninEncrypted;
        this.verified = result.verified();
        this.providerReference = result.providerReference();
        this.verifiedAt = result.verified() ? Instant.now() : null;
    }
}
