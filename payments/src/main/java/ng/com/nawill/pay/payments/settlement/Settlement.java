package ng.com.nawill.pay.payments.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigInteger;
import ng.com.nawill.pay.common.entity.BaseEntity;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccount;

/**
 * One disbursement out of a virtual account to one of its settlement
 * accounts (doc 3 §1.1: "settle, disburse" is idempotency-protected, same
 * as a transaction). {@code version} backs optimistic locking on the
 * PENDING -> PROCESSING -> COMPLETED/FAILED state machine, mirroring
 * {@code Transaction}.
 */
@Entity
@Table(name = "settlements")
public class Settlement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "virtual_account_id", nullable = false)
    private VirtualAccount virtualAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_account_id", nullable = false)
    private SettlementAccount settlementAccount;

    @Column(name = "amount", nullable = false, precision = 19, scale = 0)
    private BigInteger amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 16)
    private SettlementStatus settlementStatus = SettlementStatus.PENDING;

    @Column(name = "reference", length = 128)
    private String reference;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Settlement() {
    }

    public Settlement(VirtualAccount virtualAccount, SettlementAccount settlementAccount, BigInteger amount,
                       String idempotencyKey) {
        this.virtualAccount = virtualAccount;
        this.settlementAccount = settlementAccount;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    public VirtualAccount getVirtualAccount() {
        return virtualAccount;
    }

    public SettlementAccount getSettlementAccount() {
        return settlementAccount;
    }

    public BigInteger getAmount() {
        return amount;
    }

    public SettlementStatus getSettlementStatus() {
        return settlementStatus;
    }

    public String getReference() {
        return reference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void transitionTo(SettlementStatus next) {
        this.settlementStatus = next;
    }

    public void assignReference(String reference) {
        this.reference = reference;
    }
}
