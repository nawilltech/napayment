package ng.com.nawill.pay.payments.paymentlink;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccount;

/**
 * A shareable link a payer uses to pay a business (FR-14, doc 4 §C.9). The
 * short code IS the short URL - a dedicated general-purpose URL shortener
 * would be over-engineering here, nothing else in this MVP needs one.
 * {@code amount} null means the payer enters their own amount at pay time.
 */
@Entity
@Table(name = "payment_links")
public class PaymentLink extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "virtual_account_id", nullable = false)
    private VirtualAccount virtualAccount;

    @Column(name = "short_code", nullable = false, unique = true, length = 16)
    private String shortCode;

    @Column(name = "amount", precision = 19, scale = 0)
    private BigInteger amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 16)
    private PaymentLinkType linkType;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "single_use", nullable = false)
    private boolean singleUse;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_status", nullable = false, length = 16)
    private PaymentLinkStatus linkStatus = PaymentLinkStatus.ACTIVE;

    protected PaymentLink() {
    }

    public PaymentLink(UUID businessId, VirtualAccount virtualAccount, String shortCode, BigInteger amount,
                        String currency, PaymentLinkType linkType, Instant expiresAt, boolean singleUse) {
        this.businessId = businessId;
        this.virtualAccount = virtualAccount;
        this.shortCode = shortCode;
        this.amount = amount;
        this.currency = currency;
        this.linkType = linkType;
        this.expiresAt = expiresAt;
        this.singleUse = singleUse;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public VirtualAccount getVirtualAccount() {
        return virtualAccount;
    }

    public String getShortCode() {
        return shortCode;
    }

    public BigInteger getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentLinkType getLinkType() {
        return linkType;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isSingleUse() {
        return singleUse;
    }

    public PaymentLinkStatus getLinkStatus() {
        return linkStatus;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isPayable() {
        return linkStatus == PaymentLinkStatus.ACTIVE && !isExpired();
    }

    public void markRedeemed() {
        this.linkStatus = PaymentLinkStatus.REDEEMED;
    }

    public void markExpired() {
        this.linkStatus = PaymentLinkStatus.EXPIRED;
    }

    public void revoke() {
        this.linkStatus = PaymentLinkStatus.REVOKED;
    }

    void reassignShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public boolean isOwnedByBusiness(UUID candidateBusinessId) {
        return businessId.equals(candidateBusinessId);
    }
}
