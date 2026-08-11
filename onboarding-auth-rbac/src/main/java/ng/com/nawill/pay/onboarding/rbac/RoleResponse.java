package ng.com.nawill.pay.onboarding.rbac;

import java.util.List;
import java.util.UUID;

public record RoleResponse(UUID id, String name, UUID businessId, List<String> permissionNames) {

    public static RoleResponse from(Role role, List<String> permissionNames) {
        return new RoleResponse(role.getId(), role.getName(), role.getBusinessId(), permissionNames);
    }
}
