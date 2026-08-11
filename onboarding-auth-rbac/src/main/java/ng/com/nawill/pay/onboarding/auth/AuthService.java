package ng.com.nawill.pay.onboarding.auth;

import java.util.Set;
import ng.com.nawill.pay.common.exception.BadRequestException;
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
import org.springframework.security.authentication.BadCredentialsException;
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

    public AuthService(UserRepository userRepository, BusinessRepository businessRepository,
                        RoleRepository roleRepository, UserRoleRepository userRoleRepository,
                        PermissionResolutionService permissionResolutionService,
                        VirtualAccountProvisioningService virtualAccountProvisioningService,
                        PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.permissionResolutionService = permissionResolutionService;
        this.virtualAccountProvisioningService = virtualAccountProvisioningService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("failed login attempt");
            throw new BadCredentialsException("Invalid email or password");
        }
        log.info("user logged in: userId={}", user.getId());
        return issueTokenFor(user);
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
