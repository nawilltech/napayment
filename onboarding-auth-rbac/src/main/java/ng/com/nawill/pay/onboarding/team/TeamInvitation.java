package ng.com.nawill.pay.onboarding.team;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;

/** FR-5a: an invitation for someone to join a business under a given role. */
@Entity
@Table(name = "team_invitations")
public class TeamInvitation extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "email", nullable = false, length = 128)
    private String email;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "invitation_status", nullable = false, length = 16)
    private InvitationStatus invitationStatus = InvitationStatus.PENDING;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "message", length = 280)
    private String message;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected TeamInvitation() {
    }

    public TeamInvitation(UUID businessId, String email, UUID roleId, String message) {
        this.businessId = businessId;
        this.email = email;
        this.roleId = roleId;
        this.message = message;
        this.token = UUID.randomUUID().toString();
        this.invitedAt = Instant.now();
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getEmail() {
        return email;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public InvitationStatus getInvitationStatus() {
        return invitationStatus;
    }

    public String getToken() {
        return token;
    }

    public String getMessage() {
        return message;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void accept() {
        this.invitationStatus = InvitationStatus.ACCEPTED;
        this.respondedAt = Instant.now();
    }

    public void revoke() {
        this.invitationStatus = InvitationStatus.REVOKED;
        this.respondedAt = Instant.now();
    }
}
