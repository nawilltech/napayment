package ng.com.nawill.pay.payments.dynamicaccount;

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
 * A per-transaction, expiring bank-style account number (FR-DynAcct-1,
 * doc 4 §C.10) - the transfer-based sibling of a payment link, solving
 * bank-transfer reconciliation: a payer transfers from their own bank app
 * into a number minted just for this one payment intent, rather than the
 * platform trusting an unreliable free-text narration. Deposits land on
 * the parent {@code virtualAccount}, the business's real, permanent
 * account - this record is a routing/reconciliation shell around it, not
 * a second ledger.
 */
@Entity
@Table(name = "dynamic_virtual_accounts")
public class DynamicVirtualAccount extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "virtual_account_id", nullable = false)
    private VirtualAccount virtualAccount;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Column(name = "expected_amount", precision = 19, scale = 0)
    private BigInteger expectedAmount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "dynamic_account_status", nullable = false, length = 16)
    private DynamicAccountStatus dynamicAccountStatus = DynamicAccountStatus.ACTIVE;

    @Column(name = "reference", length = 128)
    private String reference;

    protected DynamicVirtualAccount() {
    }

    public DynamicVirtualAccount(UUID businessId, VirtualAccount virtualAccount, String accountNumber,
                                  BigInteger expectedAmount, Instant expiresAt, String reference) {
        this.businessId = businessId;
        this.virtualAccount = virtualAccount;
        this.accountNumber = accountNumber;
        this.expectedAmount = expectedAmount;
        this.expiresAt = expiresAt;
        this.reference = reference;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public VirtualAccount getVirtualAccount() {
        return virtualAccount;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public BigInteger getExpectedAmount() {
        return expectedAmount;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public DynamicAccountStatus getDynamicAccountStatus() {
        return dynamicAccountStatus;
    }

    public String getReference() {
        return reference;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isDepositable() {
        return dynamicAccountStatus == DynamicAccountStatus.ACTIVE && !isExpired();
    }

    public void markExpired() {
        this.dynamicAccountStatus = DynamicAccountStatus.EXPIRED;
    }

    public void markPaid() {
        this.dynamicAccountStatus = DynamicAccountStatus.PAID;
    }

    public void revoke() {
        this.dynamicAccountStatus = DynamicAccountStatus.REVOKED;
    }

    public boolean isOwnedByBusiness(UUID candidateBusinessId) {
        return businessId.equals(candidateBusinessId);
    }
}
