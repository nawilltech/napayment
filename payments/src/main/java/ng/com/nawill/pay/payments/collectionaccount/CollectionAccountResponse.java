package ng.com.nawill.pay.payments.collectionaccount;

import java.math.BigInteger;
import java.util.UUID;

public record CollectionAccountResponse(UUID id, UUID bankId, String accountNumber, String accountName, BigInteger balance) {

    public static CollectionAccountResponse from(CollectionAccount account) {
        return new CollectionAccountResponse(account.getId(), account.getBankId(), account.getAccountNumber(),
                account.getAccountName(), account.getBalance());
    }
}
