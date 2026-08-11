package ng.com.nawill.pay.onboarding.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import ng.com.nawill.pay.common.entity.BaseEntity;

/**
 * A fine-grained, explicit permission string, e.g. name="transactions:read",
 * resource="transactions", action="read" (doc 2 §4.2, doc 3 §2.2 - never a
 * bare role name).
 */
@Entity
@Table(name = "permissions")
public class Permission extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 64)
    private String name;

    @Column(name = "resource", nullable = false, length = 32)
    private String resource;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    protected Permission() {
    }

    public Permission(String name, String resource, String action) {
        this.name = name;
        this.resource = resource;
        this.action = action;
    }

    public String getName() {
        return name;
    }

    public String getResource() {
        return resource;
    }

    public String getAction() {
        return action;
    }
}
