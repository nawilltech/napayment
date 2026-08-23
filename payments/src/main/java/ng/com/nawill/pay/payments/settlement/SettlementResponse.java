package ng.com.nawill.pay.payments.settlement;

import java.math.BigInteger;
import java.util.UUID;

public record SettlementResponse(UUID id, UUID virtualAccountId, UUID settlementAccountId, BigInteger amount,
                                  String settlementStatus, String reference) {

    public static SettlementResponse from(Settlement settlement) {
        return new SettlementResponse(settlement.getId(), settlement.getVirtualAccount().getId(),
                settlement.getSettlementAccount().getId(), settlement.getAmount(),
                settlement.getSettlementStatus().name(), settlement.getReference());
    }
}
