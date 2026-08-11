package ng.com.nawill.pay.payments.virtualaccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigInteger;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;

/**
 * The account a user/business receives collections into (FR-1, doc 2 §4.2).
 * userId/businessId are plain UUID columns, never JPA relations, so this
 * module never reaches into onboarding-auth-rbac's entities.
 */
@Entity
@Table(name = "virtual_accounts")
public class VirtualAccount extends BaseEntity {

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "balance", nullable = false, precision = 19, scale = 0)
    private BigInteger balance = BigInteger.ZERO;

    @Column(name = "meta")
    private String meta;

    protected VirtualAccount() {
    }

    public VirtualAccount(String accountNumber, UUID userId, UUID businessId, String currency) {
        this.accountNumber = accountNumber;
        this.userId = userId;
        this.businessId = businessId;
        this.currency = currency;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getCurrency() {
        return currency;
    }

    public BigInteger getBalance() {
        return balance;
    }

    public String getMeta() {
        return meta;
    }

    void reassignAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public void credit(BigInteger amount) {
        this.balance = this.balance.add(amount);
    }

    public void debit(BigInteger amount) {
        this.balance = this.balance.subtract(amount);
    }

    public boolean isOwnedByUser(UUID candidateUserId) {
        return userId != null && userId.equals(candidateUserId);
    }

    public boolean isOwnedByBusiness(UUID candidateBusinessId) {
        return businessId != null && businessId.equals(candidateBusinessId);
    }
}
