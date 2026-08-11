package ng.com.nawill.pay.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * The authenticated principal resolved from the validated JWT, independent of
 * which module issued it. Any module can depend on this without reaching into
 * onboarding-auth-rbac's internals.
 */
public record CurrentUser(
        UUID userId,
        UUID businessId,
        String userType,
        Set<String> permissions
) {

    public boolean isSuperAdmin() {
        return "SUPERADMIN".equals(userType);
    }

    public boolean hasBusinessScope() {
        return businessId != null;
    }
}
