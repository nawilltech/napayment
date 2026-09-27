package ng.com.nawill.pay.payments.processor;

import java.util.Map;
import ng.com.nawill.pay.payments.paymentmethod.PaymentMethod;

/**
 * A payment method as it applies somewhere specific (a processor, a
 * business, a checkout): its code, the catalogue name as label, and whether
 * it can take payments there - false when the processor retired it or the
 * method is deactivated platform-wide.
 */
public record PaymentMethodOption(String method, String label, boolean active) {

    public static PaymentMethodOption of(PaymentMethod method) {
        return new PaymentMethodOption(method.getCode(), method.getName(), method.isActive());
    }

    /** A processor's method, labelled from the catalogue. */
    static PaymentMethodOption of(String code, boolean offeredByProcessor, Map<String, PaymentMethod> catalogue) {
        PaymentMethod method = catalogue.get(code);
        return method == null
                ? new PaymentMethodOption(code, code, false)
                : new PaymentMethodOption(code, method.getName(), offeredByProcessor && method.isActive());
    }
}
