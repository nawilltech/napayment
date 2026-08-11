package ng.com.nawill.pay.payments.processor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import ng.com.nawill.pay.common.entity.BaseEntity;

/**
 * Onboarded processor integration (FR-6). Activating/configuring a processor
 * is restricted to the "Supply Admin" role (permission processors:configure).
 * Whether it may route new transactions is governed by the inherited
 * ACTIVE/INACTIVE {@code status}, per doc 2 §4.2.
 */
@Entity
@Table(name = "payment_processors")
public class PaymentProcessor extends BaseEntity {

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    protected PaymentProcessor() {
    }

    public PaymentProcessor(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
