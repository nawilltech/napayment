package ng.com.nawill.pay.payments.settlement;

import java.math.BigDecimal;
import java.util.UUID;

public record SettlementAccountResponse(UUID id, UUID virtualAccountId, UUID bankAccountId, BigDecimal splitPercentage) {

    public static SettlementAccountResponse from(SettlementAccount account) {
        return new SettlementAccountResponse(account.getId(), account.getVirtualAccount().getId(),
                account.getBankAccount().getId(), account.getSplitPercentage());
    }
}
