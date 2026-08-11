package ng.com.nawill.pay.onboarding.rbac;

import java.util.List;
import ng.com.nawill.pay.common.exception.BadRequestException;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-5a: a business (non-superadmin) account creates scoped admin/staff
 * roles under itself, always businessId-filtered, never platform-wide.
 */
@Service
@Transactional
public class RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final CurrentUserResolver currentUserResolver;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository,
                        RolePermissionRepository rolePermissionRepository, CurrentUserResolver currentUserResolver) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.currentUserResolver = currentUserResolver;
    }

    public Role createBusinessScopedRole(CreateRoleRequest request) {
        var currentUser = currentUserResolver.requireCurrentUser();
        if (!currentUser.hasBusinessScope()) {
            throw new BadRequestException("Only a business-affiliated account can create scoped roles");
        }

        List<Permission> permissions = permissionRepository.findByNameIn(request.permissionNames());
        if (permissions.size() != request.permissionNames().size()) {
            throw new BadRequestException("One or more permission names are unknown");
        }

        Role role = roleRepository.save(new Role(request.name(), currentUser.businessId()));
        permissions.forEach(permission -> rolePermissionRepository.save(new RolePermission(role, permission)));

        // Privileged admin action - acting admin's userId is already in MDC.
        log.info("business-scoped role created: roleId={} businessId={} name={}",
                role.getId(), currentUser.businessId(), request.name());
        return role;
    }

    @Transactional(readOnly = true)
    public List<Role> listForCallerBusiness() {
        var currentUser = currentUserResolver.requireCurrentUser();
        if (!currentUser.hasBusinessScope()) {
            throw new BadRequestException("Only a business-affiliated account has scoped roles");
        }
        return roleRepository.findByBusinessId(currentUser.businessId());
    }

    @Transactional(readOnly = true)
    public List<String> permissionNamesOf(Role role) {
        return rolePermissionRepository.findByRoleId(role.getId()).stream()
                .map(rp -> rp.getPermission().getName())
                .toList();
    }
}
