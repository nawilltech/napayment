package ng.com.nawill.pay.payments.processor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;

/**
 * A business's own ON/OFF setting for one processor (FR-Admin-5). Only
 * exceptions to the processor's default are stored - no row means the
 * business follows {@link PaymentProcessor#isDefaultEnabled()} - so a new
 * business or processor needs no backfill and "for all businesses" is a
 * single field change plus clearing these rows.
 */
@Entity
@Table(name = "business_payment_processors")
public class BusinessPaymentProcessor extends BaseEntity {

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processor_id", nullable = false, updatable = false)
    private PaymentProcessor processor;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    protected BusinessPaymentProcessor() {
    }

    public BusinessPaymentProcessor(UUID businessId, PaymentProcessor processor, boolean enabled) {
        this.businessId = businessId;
        this.processor = processor;
        this.enabled = enabled;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public PaymentProcessor getProcessor() {
        return processor;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
