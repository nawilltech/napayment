package ng.com.nawill.pay.onboarding.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.AccountLockedException;
import ng.com.nawill.pay.common.exception.BadRequestException;
import ng.com.nawill.pay.common.exception.UnauthorizedException;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.onboarding.audit.AuditEventType;
import ng.com.nawill.pay.onboarding.audit.AuditOutcome;
import ng.com.nawill.pay.onboarding.audit.SecurityAuditService;
import ng.com.nawill.pay.onboarding.business.Business;
import ng.com.nawill.pay.onboarding.business.BusinessRepository;
import ng.com.nawill.pay.onboarding.email.EmailGateway;
import ng.com.nawill.pay.onboarding.rbac.PermissionResolutionService;
import ng.com.nawill.pay.onboarding.rbac.Role;
import ng.com.nawill.pay.onboarding.rbac.RoleRepository;
import ng.com.nawill.pay.onboarding.rbac.UserRole;
import ng.com.nawill.pay.onboarding.rbac.UserRoleRepository;
import ng.com.nawill.pay.onboarding.security.JwtService;
import ng.com.nawill.pay.onboarding.team.InvitationStatus;
import ng.com.nawill.pay.onboarding.team.TeamInvitation;
import ng.com.nawill.pay.onboarding.team.TeamInvitationRepository;
import ng.com.nawill.pay.onboarding.user.User;
import ng.com.nawill.pay.onboarding.user.UserRepository;
import ng.com.nawill.pay.onboarding.user.UserType;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccountProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Doc 4 C.1 onboarding flow, reduced to this session's scope: no OTP/2FA/KYC
 * step - TODO(FR-8/FR-8a) - signup verifies nothing beyond uniqueness and
 * immediately provisions a virtual account (FR-1) and a default role.
 */
@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String DEFAULT_USER_ROLE = "USER";
    private static final String DEFAULT_BUSINESS_OWNER_ROLE = "BUSINESS_OWNER";

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermissionResolutionService permissionResolutionService;
    private final VirtualAccountProvisioningService virtualAccountProvisioningService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenService refreshTokenService;
    private final SecurityAuditService securityAuditService;
    private final CurrentUserResolver currentUserResolver;
    private final TeamInvitationRepository teamInvitationRepository;
    private final EmailGateway emailGateway;
    private final String frontendBaseUrl;
    private final PasswordHistoryService passwordHistoryService;

    public AuthService(UserRepository userRepository, BusinessRepository businessRepository,
                        RoleRepository roleRepository, UserRoleRepository userRoleRepository,
                        PermissionResolutionService permissionResolutionService,
                        VirtualAccountProvisioningService virtualAccountProvisioningService,
                        PasswordEncoder passwordEncoder, JwtService jwtService,
                        LoginAttemptService loginAttemptService, PasswordResetService passwordResetService,
                        RefreshTokenService refreshTokenService, SecurityAuditService securityAuditService,
                        CurrentUserResolver currentUserResolver, TeamInvitationRepository teamInvitationRepository,
                        EmailGateway emailGateway, @Value("${nawill.frontend.base-url}") String frontendBaseUrl,
                        PasswordHistoryService passwordHistoryService) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.permissionResolutionService = permissionResolutionService;
        this.virtualAccountProvisioningService = virtualAccountProvisioningService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.passwordResetService = passwordResetService;
        this.refreshTokenService = refreshTokenService;
        this.securityAuditService = securityAuditService;
        this.currentUserResolver = currentUserResolver;
        this.teamInvitationRepository = teamInvitationRepository;
        this.emailGateway = emailGateway;
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
        this.passwordHistoryService = passwordHistoryService;
    }

    public AuthResponse signup(SignupRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BadRequestException("PASSWORD_MISMATCH", "Password and confirm password do not match");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("EMAIL_TAKEN", "An account with this email already exists");
        }
        if (userRepository.existsByPhoneNo(request.phoneNo())) {
            throw new BadRequestException("PHONE_TAKEN", "An account with this phone number already exists");
        }

        User user = new User(request.firstName(), request.middleName(), request.lastName(), request.email(),
                request.phoneNo(), passwordEncoder.encode(request.password()), UserType.USER);
        user = userRepository.save(user);

        String defaultRoleName;
        if (request.isBusinessSignup()) {
            Business business = businessRepository.save(new Business(request.businessName(), request.cacNumber(), user.getId()));
            user.assignBusiness(business.getId());
            user = userRepository.save(user);
            virtualAccountProvisioningService.provisionForBusiness(business.getId());
            defaultRoleName = DEFAULT_BUSINESS_OWNER_ROLE;
        } else {
            virtualAccountProvisioningService.provisionForUser(user.getId());
            defaultRoleName = DEFAULT_USER_ROLE;
        }

        assignDefaultRole(user, defaultRoleName);
        passwordHistoryService.record(user.getId(), user.getPasswordHash());
        log.info("user signed up: userId={} businessSignup={}", user.getId(), request.isBusinessSignup());
        securityAuditService.record(AuditEventType.SIGNUP, AuditOutcome.SUCCESS, user.getId(), user.getBusinessId(),
                user.getEmail(), request.isBusinessSignup() ? "Business signup" : "Individual signup");

        return issueTokenFor(user);
    }

    /**
     * FR-5a's "join an existing business" signup variant: attaches the new
     * user to the inviting business under the invitation's role, instead of
     * {@link #signup}'s always-a-new-business/individual path. No new
     * virtual account is provisioned - the business already has one from its
     * original signup, and it's shared across every user on that business.
     */
    public AuthResponse signupViaInvite(AcceptInviteRequest request) {
        TeamInvitation invitation = teamInvitationRepository.findByToken(request.token())
                .filter(i -> i.getInvitationStatus() == InvitationStatus.PENDING)
                .orElseThrow(() -> new BadRequestException("INVALID_INVITE", "This invite is no longer valid"));

        if (userRepository.existsByEmail(invitation.getEmail())) {
            throw new BadRequestException("EMAIL_TAKEN", "An account with this email already exists");
        }
        if (userRepository.existsByPhoneNo(request.phoneNo())) {
            throw new BadRequestException("PHONE_TAKEN", "An account with this phone number already exists");
        }

        User user = new User(request.firstName(), request.middleName(), request.lastName(), invitation.getEmail(),
                request.phoneNo(), passwordEncoder.encode(request.password()), UserType.USER);
        user.assignBusiness(invitation.getBusinessId());
        user = userRepository.save(user);

        Role role = roleRepository.findById(invitation.getRoleId())
                .orElseThrow(() -> new IllegalStateException("Invitation role not found: " + invitation.getRoleId()));
        userRoleRepository.save(new UserRole(user, role));

        invitation.accept();
        teamInvitationRepository.save(invitation);
        passwordHistoryService.record(user.getId(), user.getPasswordHash());
        log.info("user joined business via invite: userId={} businessId={} invitationId={}",
                user.getId(), invitation.getBusinessId(), invitation.getId());
        securityAuditService.record(AuditEventType.SIGNUP_VIA_INVITE, AuditOutcome.SUCCESS, user.getId(),
                invitation.getBusinessId(), user.getEmail(), "Joined via invitation " + invitation.getId());

        return issueTokenFor(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email();

        Optional<Duration> locked = loginAttemptService.lockedRemaining(email);
        if (locked.isPresent()) {
            securityAuditService.record(AuditEventType.LOGIN, AuditOutcome.FAILURE, null, null, email,
                    "Attempted login while account locked");
            throw new AccountLockedException(lockedMessage(locked.get()));
        }

        Optional<User> maybeUser = userRepository.findByEmail(email);
        boolean credentialsValid = maybeUser.isPresent()
                && passwordEncoder.matches(request.password(), maybeUser.get().getPasswordHash());

        if (!credentialsValid) {
            int attempts = loginAttemptService.recordFailure(email);
            UUID knownUserId = maybeUser.map(User::getId).orElse(null);
            if (attempts >= loginAttemptService.maxAttempts()) {
                loginAttemptService.lock(email);
                log.warn("account locked after {} failed login attempts: email={}", attempts, email);
                securityAuditService.record(AuditEventType.ACCOUNT_LOCKED, AuditOutcome.FAILURE, knownUserId, null,
                        email, attempts + " failed login attempts");
                throw new AccountLockedException(lockedMessage(loginAttemptService.lockoutDuration()));
            }
            log.warn("failed login attempt {}/{}", attempts, loginAttemptService.maxAttempts());
            securityAuditService.record(AuditEventType.LOGIN, AuditOutcome.FAILURE, knownUserId, null, email,
                    "Invalid credentials (attempt " + attempts + "/" + loginAttemptService.maxAttempts() + ")");
            throw new UnauthorizedException("INVALID_CREDENTIALS",
                    "Invalid email or password (attempt " + attempts + "/" + loginAttemptService.maxAttempts() + ")");
        }

        loginAttemptService.clear(email);
        User user = maybeUser.get();
        log.info("user logged in: userId={}", user.getId());
        securityAuditService.record(AuditEventType.LOGIN, AuditOutcome.SUCCESS, user.getId(), user.getBusinessId(),
                email, null);
        return issueTokenFor(user);
    }

    public ForgotPasswordResponse forgotPassword(String email) {
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isPresent()) {
            String token = passwordResetService.issueToken(email);
            log.info("password reset token issued: userId={}", user.get().getId());
            securityAuditService.record(AuditEventType.PASSWORD_RESET_REQUESTED, AuditOutcome.SUCCESS,
                    user.get().getId(), user.get().getBusinessId(), email, null);
            emailGateway.send(email, "Reset your Nawill Pay password",
                    "We received a request to reset your Nawill Pay password. Click the link below to choose a "
                            + "new one:\n\n" + resetUrl(email, token)
                            + "\n\nIf you didn't request this, you can safely ignore this email.");
        } else {
            // No account exists for this email - the response deliberately doesn't
            // reveal that (doc 3 §2.1's anti-enumeration posture), but it's still
            // worth a durable record: repeated requests against unknown emails is
            // an enumeration-attempt signal for security monitoring.
            securityAuditService.record(AuditEventType.PASSWORD_RESET_REQUESTED, AuditOutcome.FAILURE, null, null,
                    email, "No account with this email");
        }
        return new ForgotPasswordResponse("If an account with that email exists, a password reset link has been sent to it.");
    }

    /** Absolute link into napayment-fe - carries the code so the reset page doesn't need the user to retype it. */
    private String resetUrl(String email, String token) {
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
        return frontendBaseUrl + "/reset-password?email=" + encodedEmail + "&token=" + token;
    }

    public void resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new BadRequestException("PASSWORD_MISMATCH", "New password and confirm new password do not match");
        }
        boolean valid = passwordResetService.validateAndConsume(request.email(), request.token());
        if (!valid) {
            securityAuditService.record(AuditEventType.PASSWORD_RESET_COMPLETED, AuditOutcome.FAILURE, null, null,
                    request.email(), "Invalid or expired reset code");
            throw new BadRequestException("INVALID_RESET_TOKEN", "The reset code is invalid or has expired");
        }
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadRequestException("INVALID_RESET_TOKEN", "The reset code is invalid or has expired"));
        if (passwordHistoryService.isReused(user.getId(), request.newPassword(), user.getPasswordHash())) {
            securityAuditService.record(AuditEventType.PASSWORD_RESET_COMPLETED, AuditOutcome.FAILURE, user.getId(),
                    user.getBusinessId(), request.email(), "Rejected: matches a recently used password");
            throw new BadRequestException("PASSWORD_REUSED",
                    "You can't reuse one of your last 4 passwords. Choose a different password.");
        }
        user.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        passwordHistoryService.record(user.getId(), user.getPasswordHash());
        loginAttemptService.clear(request.email());
        log.info("password reset: userId={}", user.getId());
        securityAuditService.record(AuditEventType.PASSWORD_RESET_COMPLETED, AuditOutcome.SUCCESS, user.getId(),
                user.getBusinessId(), request.email(), null);
    }

    /**
     * Rotates the refresh token and issues a fresh access token. Permissions
     * are re-resolved from scratch rather than trusted from any prior state -
     * a role/permission change since the last login must take effect the
     * moment the client refreshes, not just on the next full re-login.
     */
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(request.refreshToken());
        User user = userRepository.findById(rotation.userId())
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH_TOKEN", "Invalid refresh token"));

        Set<String> permissions = permissionResolutionService.resolveFor(user.getId());
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getBusinessId(),
                user.getUserType().name(), permissions);
        log.info("access token refreshed: userId={}", user.getId());
        return AuthResponse.bearer(accessToken, rotation.rawToken(), jwtService.expiresInSeconds(),
                user.getId(), user.getBusinessId());
    }

    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    public void changePassword(ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new BadRequestException("PASSWORD_MISMATCH", "New password and confirm new password do not match");
        }
        CurrentUser currentUser = currentUserResolver.requireCurrentUser();
        User user = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + currentUser.userId()));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            securityAuditService.record(AuditEventType.PASSWORD_CHANGED, AuditOutcome.FAILURE, user.getId(),
                    user.getBusinessId(), "Current password did not match");
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Current password is incorrect");
        }
        if (passwordHistoryService.isReused(user.getId(), request.newPassword(), user.getPasswordHash())) {
            securityAuditService.record(AuditEventType.PASSWORD_CHANGED, AuditOutcome.FAILURE, user.getId(),
                    user.getBusinessId(), "Rejected: matches a recently used password");
            throw new BadRequestException("PASSWORD_REUSED",
                    "You can't reuse one of your last 4 passwords. Choose a different password.");
        }
        user.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        passwordHistoryService.record(user.getId(), user.getPasswordHash());
        log.info("password changed: userId={}", user.getId());
        securityAuditService.record(AuditEventType.PASSWORD_CHANGED, AuditOutcome.SUCCESS, user.getId(),
                user.getBusinessId(), null);
    }

    private String lockedMessage(Duration remaining) {
        long minutes = Math.max(1, remaining.toMinutes());
        return "Account locked due to too many failed login attempts. Try again in " + minutes + " minute(s).";
    }

    private void assignDefaultRole(User user, String roleName) {
        Role role = roleRepository.findByNameAndBusinessIdIsNull(roleName)
                .orElseThrow(() -> new IllegalStateException("Default role not seeded: " + roleName));
        userRoleRepository.save(new UserRole(user, role));
    }

    private AuthResponse issueTokenFor(User user) {
        Set<String> permissions = permissionResolutionService.resolveFor(user.getId());
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getBusinessId(),
                user.getUserType().name(), permissions);
        String refreshToken = refreshTokenService.issue(user.getId());
        return AuthResponse.bearer(accessToken, refreshToken, jwtService.expiresInSeconds(),
                user.getId(), user.getBusinessId());
    }
}
