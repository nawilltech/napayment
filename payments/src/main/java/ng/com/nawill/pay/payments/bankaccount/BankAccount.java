package ng.com.nawill.pay.payments.bankaccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;

/**
 * A real external bank account a business has registered, later attached to
 * a {@code SettlementAccount} with a split percentage (doc 2 §4.2, FR-2).
 * {@code bankId} is a bare UUID validated against reference-data's
 * BankRepository at creation time, never a JPA relation across modules -
 * matches the convention already established by VirtualAccount/Transaction.
 */
@Entity
@Table(name = "bank_accounts")
public class BankAccount extends BaseEntity {

    @Column(name = "bank_id", nullable = false)
    private UUID bankId;

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @Column(name = "account_name", nullable = false, length = 128)
    private String accountName;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    protected BankAccount() {
    }

    public BankAccount(UUID bankId, String accountNumber, String accountName, UUID businessId) {
        this.bankId = bankId;
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.businessId = businessId;
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

    public UUID getBusinessId() {
        return businessId;
    }

    public boolean isOwnedByBusiness(UUID candidateBusinessId) {
        return businessId.equals(candidateBusinessId);
    }
}
