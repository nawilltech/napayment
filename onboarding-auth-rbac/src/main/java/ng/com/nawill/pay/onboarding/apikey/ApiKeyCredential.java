package ng.com.nawill.pay.onboarding.apikey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;

/**
 * A business's public/secret key pair (FR-9: "public key and secret key
 * pairs"), used to HMAC-sign third-party requests to /api/v1/collect and
 * /api/v1/withdraw (doc 3 §2.5). One active pair per business - regenerating
 * revokes the old row (status INACTIVE) and inserts a new one, rather than
 * updating in place, so a leaked-key revocation is auditable.
 * {@code secretKeyEncrypted} is reversible (AES-GCM via EncryptionService),
 * never a one-way hash, because verifying a signature requires recovering
 * the plaintext secret.
 */
@Entity
@Table(name = "api_keys")
public class ApiKeyCredential extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "public_key", nullable = false, unique = true, length = 64)
    private String publicKey;

    @Column(name = "secret_key_encrypted", nullable = false, columnDefinition = "text")
    private String secretKeyEncrypted;

    protected ApiKeyCredential() {
    }

    public ApiKeyCredential(UUID businessId, String publicKey, String secretKeyEncrypted) {
        this.businessId = businessId;
        this.publicKey = publicKey;
        this.secretKeyEncrypted = secretKeyEncrypted;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getSecretKeyEncrypted() {
        return secretKeyEncrypted;
    }
}
