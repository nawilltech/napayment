package ng.com.nawill.pay.payments.processor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import ng.com.nawill.pay.common.entity.BaseEntity;
import ng.com.nawill.pay.common.entity.EntityStatus;

/**
 * A payment method a processor offers (FR-Proc-2). The inherited status
 * enables (ACTIVE) or retires (INACTIVE) it; a retired method keeps its row
 * so history stays intact and re-enabling reuses it.
 */
@Entity
@Table(name = "payment_processor_methods")
public class PaymentProcessorMethod extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processor_id", nullable = false)
    private PaymentProcessor processor;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 16)
    private PaymentMethod method;

    protected PaymentProcessorMethod() {
    }

    PaymentProcessorMethod(PaymentProcessor processor, PaymentMethod method) {
        this.processor = processor;
        this.method = method;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public boolean isActive() {
        return getStatus() == EntityStatus.ACTIVE;
    }
}
