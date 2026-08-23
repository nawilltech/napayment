package ng.com.nawill.pay.payments.paymentlink;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

public record PaymentLinkResponse(UUID id, String shortCode, BigInteger amount, String currency, String linkType,
                                   Instant expiresAt, boolean singleUse, String linkStatus) {

    public static PaymentLinkResponse from(PaymentLink link) {
        return new PaymentLinkResponse(link.getId(), link.getShortCode(), link.getAmount(), link.getCurrency(),
                link.getLinkType().name(), link.getExpiresAt(), link.isSingleUse(), link.getLinkStatus().name());
    }
}
