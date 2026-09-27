package ng.com.nawill.pay.payments.processor;

/** A payment method with its display label; {@code active} is false for a method a processor has retired. */
public record PaymentMethodResponse(PaymentMethod method, String label, boolean active) {

    public static PaymentMethodResponse of(PaymentMethod method, boolean active) {
        return new PaymentMethodResponse(method, method.label(), active);
    }
}
