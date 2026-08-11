package ng.com.nawill.pay.common.security;

import org.springframework.stereotype.Component;

/**
 * SpEL entry point for permission-string based {@code @PreAuthorize} checks,
 * e.g. {@code @PreAuthorize("@auth.can('transactions:read')")}. Method-level
 * checks are always expressed against an explicit permission string, never a
 * bare role name (doc 3 §2.2).
 * <p>
 * SUPERADMIN's access is hard-coded here rather than assembled from the
 * permissions table: it short-circuits to {@code true} whenever the current
 * principal carries {@link SuperAdminAuthority#AUTHORITY}, so a compromised
 * permissions table can never silently grant superadmin elsewhere.
 */
@Component("auth")
public class PermissionChecker {

    private final CurrentUserResolver currentUserResolver;

    public PermissionChecker(CurrentUserResolver currentUserResolver) {
        this.currentUserResolver = currentUserResolver;
    }

    public boolean can(String permission) {
        return currentUserResolver.resolve()
                .map(user -> user.isSuperAdmin() || user.permissions().contains(permission))
                .orElse(false);
    }
}
