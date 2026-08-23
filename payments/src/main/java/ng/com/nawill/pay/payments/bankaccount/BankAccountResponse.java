package ng.com.nawill.pay.payments.bankaccount;

import java.util.UUID;

public record BankAccountResponse(UUID id, UUID bankId, String accountNumber, String accountName) {

    public static BankAccountResponse from(BankAccount bankAccount) {
        return new BankAccountResponse(bankAccount.getId(), bankAccount.getBankId(),
                bankAccount.getAccountNumber(), bankAccount.getAccountName());
    }
}
