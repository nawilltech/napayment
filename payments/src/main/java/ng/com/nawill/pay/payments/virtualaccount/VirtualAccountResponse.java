package ng.com.nawill.pay.payments.virtualaccount;

import java.math.BigInteger;
import java.util.UUID;

public record VirtualAccountResponse(
        UUID id,
        String accountNumber,
        UUID userId,
        UUID businessId,
        String currency,
        BigInteger balance
) {

    public static VirtualAccountResponse from(VirtualAccount account) {
        return new VirtualAccountResponse(account.getId(), account.getAccountNumber(), account.getUserId(),
                account.getBusinessId(), account.getCurrency(), account.getBalance());
    }
}
