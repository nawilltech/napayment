package ng.com.nawill.pay.payments.dynamicaccount;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

public record DynamicVirtualAccountResponse(UUID id, String accountNumber, BigInteger expectedAmount,
                                             Instant expiresAt, String status, String reference) {

    public static DynamicVirtualAccountResponse from(DynamicVirtualAccount account) {
        return new DynamicVirtualAccountResponse(account.getId(), account.getAccountNumber(),
                account.getExpectedAmount(), account.getExpiresAt(),
                account.getDynamicAccountStatus().name(), account.getReference());
    }
}
