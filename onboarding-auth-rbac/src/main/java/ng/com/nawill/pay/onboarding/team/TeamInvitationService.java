package ng.com.nawill.pay.onboarding.team;

import java.util.List;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.common.audit.AuditEventType;
import ng.com.nawill.pay.common.audit.AuditOutcome;
import ng.com.nawill.pay.onboarding.audit.SecurityAuditService;
import ng.com.nawill.pay.onboarding.email.EmailGateway;
import ng.com.nawill.pay.onboarding.rbac.Permission;
import ng.com.nawill.pay.onboarding.rbac.PermissionRepository;
import ng.com.nawill.pay.onboarding.rbac.Role;
import ng.com.nawill.pay.onboarding.rbac.RolePermission;
import ng.com.nawill.pay.onboarding.rbac.RolePermissionRepository;
import ng.com.nawill.pay.onboarding.rbac.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-5a: inviting a teammate to join the caller's business under a role template. */
@Service
@Transactional
public class TeamInvitationService {

    private static final Logger log = LoggerFactory.getLogger(TeamInvitationService.class);

    private final TeamInvitationRepository teamInvitationRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final EmailGateway emailGateway;
    private final SecurityAuditService securityAuditService;
    private final CurrentUserResolver currentUserResolver;
    private final String frontendBaseUrl;

    public TeamInvitationService(TeamInvitationRepository teamInvitationRepository, RoleRepository roleRepository,
                                  PermissionRepository permissionRepository,
                                  RolePermissionRepository rolePermissionRepository, EmailGateway emailGateway,
                                  SecurityAuditService securityAuditService, CurrentUserResolver currentUserResolver,
                                  @Value("${nawill.frontend.base-url}") String frontendBaseUrl) {
        this.teamInvitationRepository = teamInvitationRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.emailGateway = emailGateway;
        this.securityAuditService = securityAuditService;
        this.currentUserResolver = currentUserResolver;
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
    }

    public TeamInvitation create(CreateInviteRequest request) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        Role role = findOrCreateRole(currentUser.businessId(), request.roleTemplate());

        TeamInvitation invitation = teamInvitationRepository.save(
                new TeamInvitation(currentUser.businessId(), request.email(), role.getId(), request.message()));

        log.info("team invitation created: invitationId={} businessId={} roleTemplate={}",
                invitation.getId(), currentUser.businessId(), request.roleTemplate());
        securityAuditService.record(AuditEventType.TEAM_INVITATION_CREATED, AuditOutcome.SUCCESS,
                currentUser.userId(), currentUser.businessId(), request.email(),
                "Invited as " + request.roleTemplate().roleName());
        emailGateway.send(request.email(), "You've been invited to join Nawill Pay",
                "You've been invited to join a business on Nawill Pay as " + request.roleTemplate().roleName()
                        + ". Accept your invite: " + inviteUrl(invitation)
                        + (request.message() == null ? "" : "\n\nMessage from the inviter: " + request.message()));

        return invitation;
    }

    /** Absolute link into napayment-fe - a bare "/invite/&lt;token&gt;" path is meaningless outside a browser session. */
    public String inviteUrl(TeamInvitation invitation) {
        return frontendBaseUrl + "/invite/" + invitation.getToken();
    }

    @Transactional(readOnly = true)
    public List<TeamInvitation> list() {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        return teamInvitationRepository.findByBusinessId(currentUser.businessId());
    }

    public void revoke(UUID invitationId) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        TeamInvitation invitation = teamInvitationRepository.findByIdAndBusinessId(invitationId, currentUser.businessId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVITATION_NOT_FOUND));
        invitation.revoke();
        teamInvitationRepository.save(invitation);
        securityAuditService.record(AuditEventType.TEAM_INVITATION_REVOKED, AuditOutcome.SUCCESS,
                currentUser.userId(), currentUser.businessId(), invitation.getEmail(), null);
    }

    /**
     * Reuses an existing role of the same name for this business (created by
     * an earlier invite), or creates it with the template's fixed permission
     * set - mirrors what the frontend used to do itself via {@code POST
     * /api/v1/roles} before this endpoint existed.
     */
    private Role findOrCreateRole(UUID businessId, RoleTemplate template) {
        return roleRepository.findByBusinessIdAndName(businessId, template.roleName())
                .orElseGet(() -> {
                    Role role = roleRepository.save(new Role(template.roleName(), businessId));
                    List<Permission> permissions = permissionRepository.findByNameIn(template.permissionNames());
                    permissions.forEach(permission -> rolePermissionRepository.save(new RolePermission(role, permission)));
                    return role;
                });
    }
}
