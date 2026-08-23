package ng.com.nawill.pay.payments.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import ng.com.nawill.pay.common.entity.BaseEntity;
import ng.com.nawill.pay.payments.bankaccount.BankAccount;
import ng.com.nawill.pay.payments.virtualaccount.VirtualAccount;

/**
 * The bank account a virtual account's funds settle to, with a split
 * percentage (doc 2 §4.2, FR-2). A virtual account may have several of
 * these; {@code SettlementService} rejects a create/update that would push
 * the sum of active split percentages for one virtual account above 100.
 */
@Entity
@Table(name = "settlement_accounts")
public class SettlementAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "virtual_account_id", nullable = false)
    private VirtualAccount virtualAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private BankAccount bankAccount;

    @Column(name = "split_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal splitPercentage;

    protected SettlementAccount() {
    }

    public SettlementAccount(VirtualAccount virtualAccount, BankAccount bankAccount, BigDecimal splitPercentage) {
        this.virtualAccount = virtualAccount;
        this.bankAccount = bankAccount;
        this.splitPercentage = splitPercentage;
    }

    public VirtualAccount getVirtualAccount() {
        return virtualAccount;
    }

    public BankAccount getBankAccount() {
        return bankAccount;
    }

    public BigDecimal getSplitPercentage() {
        return splitPercentage;
    }
}
