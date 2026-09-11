package ng.com.nawill.pay.onboarding.team;

import java.util.List;
import java.util.Map;

/**
 * The team-invite role presets a business owner picks from (FR-5a). Mirrors
 * {@code ROLE_TEMPLATE_META} in the frontend's
 * {@code packages/schemas/src/onboarding.ts} exactly, now enforced here
 * instead of the frontend deriving a permission list and calling
 * {@code POST /api/v1/roles} itself.
 */
public enum RoleTemplate {
    ADMIN,
    DEVELOPER,
    ACCOUNT_OFFICER;

    private static final Map<RoleTemplate, List<String>> PERMISSION_NAMES = Map.of(
            ADMIN, List.of("transactions:read", "transactions:create", "virtualaccounts:read", "settlements:read",
                    "settlements:manage", "apikeys:manage", "paymentlinks:manage", "paymentlinks:read",
                    "temporaryaccounts:manage", "roles:manage"),
            DEVELOPER, List.of("apikeys:manage", "paymentlinks:read", "temporaryaccounts:manage"),
            ACCOUNT_OFFICER, List.of("transactions:read", "settlements:read", "virtualaccounts:read")
    );

    public List<String> permissionNames() {
        return PERMISSION_NAMES.get(this);
    }

    /** The role name this template is stored/looked-up under for a business. */
    public String roleName() {
        return switch (this) {
            case ADMIN -> "Admin";
            case DEVELOPER -> "Developer";
            case ACCOUNT_OFFICER -> "Account Officer";
        };
    }
}
