package ng.com.nawill.pay.payments.collectionaccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigInteger;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;

/**
 * Nawill Pay's single pooled bank account (doc 1 §1.3 glossary "Collection
 * Account"; doc 3 §3's reconciliation invariant) - where real inbound
 * transfers land before internal ledger allocation. Deliberately not named
 * SettlementAccount, which is reserved for a business's own external
 * settlement bank account (doc 2 §4.2). Admin-only to create
 * (collection-account:manage, unassigned to any seeded role - SUPERADMIN
 * bypass only); enforced as a singleton at the DB level via a partial
 * unique index on status = 'ACTIVE' (see V0019 migration), backstopped
 * here by an explicit existing-active-row check for a clearer error.
 * {@code balance} mirrors every VirtualAccount credit/settlement debit
 * (ADR-5, doc 2 §7), making the "sum of virtual-account balances
 * reconciles to the pooled balance" invariant mechanically checkable.
 */
@Entity
@Table(name = "collection_accounts")
public class CollectionAccount extends BaseEntity {

    @Column(name = "bank_id", nullable = false)
    private UUID bankId;

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @Column(name = "account_name", nullable = false, length = 128)
    private String accountName;

    @Column(name = "balance", nullable = false, precision = 19, scale = 0)
    private BigInteger balance = BigInteger.ZERO;

    protected CollectionAccount() {
    }

    public CollectionAccount(UUID bankId, String accountNumber, String accountName) {
        this.bankId = bankId;
        this.accountNumber = accountNumber;
        this.accountName = accountName;
    }

    public UUID getBankId() {
        return bankId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getAccountName() {
        return accountName;
    }

    public BigInteger getBalance() {
        return balance;
    }

    public void credit(BigInteger amount) {
        this.balance = this.balance.add(amount);
    }

    public void debit(BigInteger amount) {
        this.balance = this.balance.subtract(amount);
    }
}
