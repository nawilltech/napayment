package ng.com.nawill.pay.payments.processor;

/**
 * The fixed platform list of ways a payer can pay (FR-Proc-2, "Payment
 * Method" in the requirements). A processor offers a subset of these. Fixed
 * in code rather than admin-defined because each method needs its own
 * integration path; adding one is a code change either way.
 */
public enum PaymentMethod {
    TRANSFER("Transfer"),
    CARD("Card"),
    USSD("USSD"),
    BANK_DEBIT("Bank debit"),
    QR("QR");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    /** Human-readable name used in user-facing messages. */
    public String label() {
        return label;
    }
}
