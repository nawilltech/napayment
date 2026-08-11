package ng.com.nawill.pay.onboarding.rbac;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles a user's permission strings from role_permission at
 * token-issuance time. Never called for SUPERADMIN - its access is
 * hard-coded in {@code PermissionChecker} instead (doc 3 §2.2).
 */
@Service
@Transactional(readOnly = true)
public class PermissionResolutionService {

    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public PermissionResolutionService(UserRoleRepository userRoleRepository,
                                        RolePermissionRepository rolePermissionRepository) {
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public Set<String> resolveFor(UUID userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRole)
                .flatMap(role -> rolePermissionRepository.findByRoleId(role.getId()).stream())
                .map(rp -> rp.getPermission().getName())
                .collect(Collectors.toUnmodifiableSet());
    }
}
