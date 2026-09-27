package ng.com.nawill.pay.onboarding.team;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Team & Roles")
@RestController
@RequestMapping("/api/v1/team/invitations")
public class TeamInvitationController {

    private final TeamInvitationService teamInvitationService;

    public TeamInvitationController(TeamInvitationService teamInvitationService) {
        this.teamInvitationService = teamInvitationService;
    }

    @Operation(summary = "Invite a team member with a role template")
    @PostMapping
    @PreAuthorize("@auth.can('roles:manage')")
    public ResponseEntity<InviteResponse> create(@Valid @RequestBody CreateInviteRequest request) {
        TeamInvitation invitation = teamInvitationService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/team/invitations/" + invitation.getId()))
                .body(InviteResponse.from(invitation, teamInvitationService.inviteUrl(invitation)));
    }

    @Operation(summary = "List the business's team invitations")
    @GetMapping
    @PreAuthorize("@auth.can('roles:manage')")
    public List<InviteResponse> list() {
        return teamInvitationService.list().stream()
                .map(invitation -> InviteResponse.from(invitation, teamInvitationService.inviteUrl(invitation)))
                .toList();
    }

    @Operation(summary = "Revoke a pending team invitation")
    @DeleteMapping("/{id}")
    @PreAuthorize("@auth.can('roles:manage')")
    public OkResponse revoke(@PathVariable UUID id) {
        teamInvitationService.revoke(id);
        return OkResponse.OK;
    }
}
