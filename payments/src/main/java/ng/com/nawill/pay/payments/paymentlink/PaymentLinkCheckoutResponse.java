package ng.com.nawill.pay.payments.paymentlink;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;
import ng.com.nawill.pay.payments.processor.PaymentMethodOption;

/** What a payer sees before paying: the link plus the methods it can be paid with right now (FR-Proc-3). */
public record PaymentLinkCheckoutResponse(@JsonUnwrapped PaymentLinkResponse link,
                                          List<PaymentMethodOption> availableMethods) {
}
