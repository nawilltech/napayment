package ng.com.nawill.pay.payments.processor;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ng.com.nawill.pay.common.entity.BaseEntity;
import ng.com.nawill.pay.common.entity.EntityStatus;

/**
 * A platform payment processor (FR-6, FR-Proc-1). The inherited status is
 * the platform-wide switch: INACTIVE means no business may use it, while
 * each business's own setting is kept for when it is reactivated.
 * {@code defaultEnabled} is the "for all businesses" default a business
 * without its own setting follows (FR-Proc-3); {@code priority} orders
 * routing, lowest first (FR-Proc-4). API keys/secrets are deployment
 * configuration keyed by {@code code}, never stored here.
 */
@Entity
@Table(name = "payment_processors")
public class PaymentProcessor extends BaseEntity {

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "code", nullable = false, length = 32, updatable = false)
    private String code;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "default_enabled", nullable = false)
    private boolean defaultEnabled;

    /** Optional base64 data URL (validated by {@link ProcessorLogo}); null = no logo. */
    @Column(name = "logo", columnDefinition = "TEXT")
    private String logo;

    @OneToMany(mappedBy = "processor", cascade = CascadeType.ALL)
    @OrderBy("method")
    private List<PaymentProcessorMethod> methods = new ArrayList<>();

    protected PaymentProcessor() {
    }

    public PaymentProcessor(String name, String code, int priority, boolean defaultEnabled) {
        this.name = name;
        this.code = code;
        this.priority = priority;
        this.defaultEnabled = defaultEnabled;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    public String getLogo() {
        return logo;
    }

    /** Null removes the logo. */
    public void setLogo(String logo) {
        this.logo = logo == null ? null : ProcessorLogo.validate(logo);
    }

    public boolean isActive() {
        return getStatus() == EntityStatus.ACTIVE;
    }

    public List<PaymentProcessorMethod> getMethods() {
        return methods;
    }

    public boolean offers(String method) {
        return findMethod(method).filter(PaymentProcessorMethod::isActive).isPresent();
    }

    public List<String> activeMethods() {
        return methods.stream().filter(PaymentProcessorMethod::isActive).map(PaymentProcessorMethod::getMethod).toList();
    }

    public void rename(String name) {
        this.name = name;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setDefaultEnabled(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    /** Adds the method, or re-enables it if it was retired. */
    public void enableMethod(String method) {
        findMethod(method).ifPresentOrElse(
                existing -> existing.setStatus(EntityStatus.ACTIVE),
                () -> methods.add(new PaymentProcessorMethod(this, method)));
    }

    /** Retires the method; returns false if this processor never offered it. */
    public boolean disableMethod(String method) {
        Optional<PaymentProcessorMethod> existing = findMethod(method);
        existing.ifPresent(m -> m.setStatus(EntityStatus.INACTIVE));
        return existing.isPresent();
    }

    private Optional<PaymentProcessorMethod> findMethod(String method) {
        return methods.stream().filter(m -> m.getMethod().equals(method)).findFirst();
    }
}
