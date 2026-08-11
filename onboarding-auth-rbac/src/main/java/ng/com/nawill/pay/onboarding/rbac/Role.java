package ng.com.nawill.pay.onboarding.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;

/**
 * A named, business-scoped or platform-scoped bundle of permissions.
 * businessId null means a platform-level role (doc 2 §4.2). Business-created
 * roles (FR-5a) are always scoped by businessId, enforced in
 * {@link RoleService}, never platform-wide.
 */
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "business_id")
    private UUID businessId;

    protected Role() {
    }

    public Role(String name, UUID businessId) {
        this.name = name;
        this.businessId = businessId;
    }

    public String getName() {
        return name;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public boolean isPlatformRole() {
        return businessId == null;
    }
}
