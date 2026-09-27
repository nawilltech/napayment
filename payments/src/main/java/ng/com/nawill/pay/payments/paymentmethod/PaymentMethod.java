package ng.com.nawill.pay.payments.paymentmethod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import ng.com.nawill.pay.common.entity.BaseEntity;
import ng.com.nawill.pay.common.entity.EntityStatus;

/**
 * A way a payer can pay (FR-Proc-2), managed in the admin console. {@code
 * code} is the permanent identifier processors' methods and transactions
 * reference, so it never changes after creation; the inherited status is the
 * platform-wide switch (INACTIVE = no processor may take payments with it).
 * Seeded with Bank transfer, Card, USSD, Bank debit and QR code (V0048).
 */
@Entity
@Table(name = "payment_methods")
public class PaymentMethod extends BaseEntity {

    /** Used when a collection doesn't say how the payer pays. */
    public static final String DEFAULT_CODE = "TRANSFER";

    @Column(name = "code", nullable = false, length = 32, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "description", length = 256)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
    /** Archived (soft-deleted): hidden from lists and new use, kept for history; null = not archived. */
    @Column(name = "archived_at")
    private Instant archivedAt;

    protected PaymentMethod() {
    }

    public PaymentMethod(String code, String name, String description, int displayOrder) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.displayOrder = displayOrder;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return getStatus() == EntityStatus.ACTIVE;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    /** Hides it and stops new use; nothing is deleted, so history still resolves and it can be restored. */
    public void archive() {
        setStatus(EntityStatus.INACTIVE);
        this.archivedAt = Instant.now();
    }

    /** Back in lists, still inactive - reactivating is a separate, deliberate step. */
    public void restore() {
        this.archivedAt = null;
    }
}
