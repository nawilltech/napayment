package ng.com.nawill.pay.payments.transaction;

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
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;
import ng.com.nawill.pay.payments.processor.PaymentProcessor;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccount;

/**
 * The core transaction ledger (doc 2 §4.2, FR-Txn-1/2). {@code version} backs
 * optimistic locking on the PENDING -> PROCESSING -> PAID/FAILED state
 * machine so two workers can never both flip the same transaction (doc 3
 * §1.2). recipientAccountId (external settlement) is deliberately not
 * modelled yet - TODO(FR-2): split settlement to SettlementAccount/BankAccount
 * is v0.2 scope; MVP credits/debits the virtual account directly.
 * <p>
 * {@code paymentProcessor} is nullable: a peer-to-peer transfer
 * (FR-Auth-1, see {@code payments.transfer}) touches no external processor
 * at all, so null is the honest value rather than a seeded fake "internal"
 * processor row (doc 2 §7 ADR-12). {@code transferGroupId} /
 * {@code counterpartyAccountId} are populated only on transfer-sourced rows -
 * a transfer produces exactly two of these (a DEBIT and a CREDIT) sharing one
 * {@code transferGroupId}, each naming the other side as its counterparty.
 */
@Entity
@Table(name = "transactions")
public class Transaction extends BaseEntity {

    @Column(name = "amount", nullable = false, precision = 19, scale = 0)
    private BigInteger amount;

    @Column(name = "charge", nullable = false, precision = 19, scale = 0)
    private BigInteger charge = BigInteger.ZERO;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_processor_id")
    private PaymentProcessor paymentProcessor;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false, length = 16)
    private TransactionStatus transactionStatus = TransactionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 8)
    private TransactionType transactionType;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "virtual_account_id", nullable = false)
    private VirtualAccount virtualAccount;

    @Column(name = "transfer_group_id")
    private UUID transferGroupId;

    @Column(name = "counterparty_account_id")
    private UUID counterpartyAccountId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Transaction() {
    }

    public Transaction(BigInteger amount, String idempotencyKey, PaymentProcessor paymentProcessor,
                        TransactionType transactionType, String sessionId, VirtualAccount virtualAccount) {
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.paymentProcessor = paymentProcessor;
        this.transactionType = transactionType;
        this.sessionId = sessionId;
        this.virtualAccount = virtualAccount;
    }

    /** FR-Auth-1: one leg of a peer-to-peer transfer - no external processor involved. */
    public Transaction(BigInteger amount, String idempotencyKey, TransactionType transactionType, String sessionId,
                        VirtualAccount virtualAccount, UUID transferGroupId, UUID counterpartyAccountId) {
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.transactionType = transactionType;
        this.sessionId = sessionId;
        this.virtualAccount = virtualAccount;
        this.transferGroupId = transferGroupId;
        this.counterpartyAccountId = counterpartyAccountId;
    }

    public BigInteger getAmount() {
        return amount;
    }

    public BigInteger getCharge() {
        return charge;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public PaymentProcessor getPaymentProcessor() {
        return paymentProcessor;
    }

    public TransactionStatus getTransactionStatus() {
        return transactionStatus;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public String getSessionId() {
        return sessionId;
    }

    public VirtualAccount getVirtualAccount() {
        return virtualAccount;
    }

    public UUID getTransferGroupId() {
        return transferGroupId;
    }

    public UUID getCounterpartyAccountId() {
        return counterpartyAccountId;
    }

    public Long getVersion() {
        return version;
    }

    public void transitionTo(TransactionStatus next) {
        this.transactionStatus = next;
    }
}
