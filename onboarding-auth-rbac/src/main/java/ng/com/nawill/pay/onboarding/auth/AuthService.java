package ng.com.nawill.pay.onboarding.auth;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import ng.com.nawill.pay.common.exception.AccountLockedException;
import ng.com.nawill.pay.common.exception.BadRequestException;
import ng.com.nawill.pay.common.exception.UnauthorizedException;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.onboarding.business.Business;
import ng.com.nawill.pay.onboarding.business.BusinessRepository;
import ng.com.nawill.pay.onboarding.rbac.PermissionResolutionService;
import ng.com.nawill.pay.onboarding.rbac.Role;
import ng.com.nawill.pay.onboarding.rbac.RoleRepository;
import ng.com.nawill.pay.onboarding.rbac.UserRole;
import ng.com.nawill.pay.onboarding.rbac.UserRoleRepository;
import ng.com.nawill.pay.onboarding.security.JwtService;
import ng.com.nawill.pay.onboarding.user.User;
import ng.com.nawill.pay.onboarding.user.UserRepository;
import ng.com.nawill.pay.onboarding.user.UserType;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccountProvisioningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final CurrentUserResolver currentUserResolver;

    public AuthService(UserRepository userRepository, BusinessRepository businessRepository,
                        RoleRepository roleRepository, UserRoleRepository userRoleRepository,
                        PermissionResolutionService permissionResolutionService,
                        VirtualAccountProvisioningService virtualAccountProvisioningService,
                        PasswordEncoder passwordEncoder, JwtService jwtService,
                        LoginAttemptService loginAttemptService, PasswordResetService passwordResetService,
                        CurrentUserResolver currentUserResolver) {
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
        this.currentUserResolver = currentUserResolver;
    }

    public AuthResponse signup(SignupRequest request) {
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
        log.info("user signed up: userId={} businessSignup={}", user.getId(), request.isBusinessSignup());

        return issueTokenFor(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email();

        Optional<Duration> locked = loginAttemptService.lockedRemaining(email);
        if (locked.isPresent()) {
            throw new AccountLockedException(lockedMessage(locked.get()));
        }

        Optional<User> maybeUser = userRepository.findByEmail(email);
        boolean credentialsValid = maybeUser.isPresent()
                && passwordEncoder.matches(request.password(), maybeUser.get().getPasswordHash());

        if (!credentialsValid) {
            int attempts = loginAttemptService.recordFailure(email);
            if (attempts >= loginAttemptService.maxAttempts()) {
                loginAttemptService.lock(email);
                log.warn("account locked after {} failed login attempts: email={}", attempts, email);
                throw new AccountLockedException(lockedMessage(loginAttemptService.lockoutDuration()));
            }
            log.warn("failed login attempt {}/{}", attempts, loginAttemptService.maxAttempts());
            throw new UnauthorizedException("INVALID_CREDENTIALS",
                    "Invalid email or password (attempt " + attempts + "/" + loginAttemptService.maxAttempts() + ")");
        }

        loginAttemptService.clear(email);
        User user = maybeUser.get();
        log.info("user logged in: userId={}", user.getId());
        return issueTokenFor(user);
    }

    public ForgotPasswordResponse forgotPassword(String email) {
        Optional<User> user = userRepository.findByEmail(email);
        String token = null;
        if (user.isPresent()) {
            token = passwordResetService.issueToken(email);
            log.info("password reset token issued: userId={}", user.get().getId());
        }
        return new ForgotPasswordResponse(
                "If an account with that email exists, a password reset code has been issued.", token);
    }

    public void resetPassword(ResetPasswordRequest request) {
        boolean valid = passwordResetService.validateAndConsume(request.email(), request.token());
        if (!valid) {
            throw new BadRequestException("INVALID_RESET_TOKEN", "The reset code is invalid or has expired");
        }
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadRequestException("INVALID_RESET_TOKEN", "The reset code is invalid or has expired"));
        user.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        loginAttemptService.clear(request.email());
        log.info("password reset: userId={}", user.getId());
    }

    public void changePassword(ChangePasswordRequest request) {
        CurrentUser currentUser = currentUserResolver.requireCurrentUser();
        User user = userRepository.findById(currentUser.userId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + currentUser.userId()));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Current password is incorrect");
        }
        user.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        log.info("password changed: userId={}", user.getId());
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
        String token = jwtService.issueAccessToken(user.getId(), user.getBusinessId(),
                user.getUserType().name(), permissions);
        return AuthResponse.bearer(token, jwtService.expiresInSeconds(), user.getId(), user.getBusinessId());
    }
}
