package ng.com.nawill.pay.onboarding.team;

import java.time.Instant;
import java.util.UUID;

public record InviteResponse(
        UUID id,
        String email,
        UUID roleId,
        InvitationStatus status,
        String inviteUrl,
        Instant invitedAt
) {

    public static InviteResponse from(TeamInvitation invitation) {
        return new InviteResponse(invitation.getId(), invitation.getEmail(), invitation.getRoleId(),
                invitation.getInvitationStatus(), "/invite/" + invitation.getToken(), invitation.getInvitedAt());
    }
}
