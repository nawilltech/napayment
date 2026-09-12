package ng.com.nawill.pay.onboarding.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;

/**
 * NFR-7: a rotating, server-revocable refresh token. Postgres-backed (not
 * Redis) - see {@link RefreshTokenService}'s Javadoc for why. {@code
 * tokenHash} is a SHA-256 hex digest - a refresh token is only ever
 * compared, never recovered, so unlike {@code ApiKeyCredential.secretKeyEncrypted}
 * (reversible AES, since an HMAC signature needs the plaintext secret back)
 * this is a plain irreversible hash, same posture as a password.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;

    protected RefreshToken() {
    }

    public RefreshToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedByTokenId() {
        return replacedByTokenId;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public void revoke(UUID replacedByTokenId) {
        this.revokedAt = Instant.now();
        this.replacedByTokenId = replacedByTokenId;
    }
}
