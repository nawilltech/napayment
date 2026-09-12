package ng.com.nawill.pay.onboarding.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;

/**
 * PCI DSS 8.3.7: a password must not match any of the account's last 4
 * passwords. {@code passwordHash} is a BCrypt hash - the same encoder and
 * the same posture as {@code User.passwordHash} - plaintext is never stored
 * here. See {@link PasswordHistoryService} for the only code that reads or
 * writes this table.
 */
@Entity
@Table(name = "password_history")
public class PasswordHistory extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    protected PasswordHistory() {
    }

    public PasswordHistory(UUID userId, String passwordHash) {
        this.userId = userId;
        this.passwordHash = passwordHash;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
