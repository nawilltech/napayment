package ng.com.nawill.pay.common.logging;

/**
 * Masks sensitive values (BVN, NIN, account numbers, passwords, tokens,
 * idempotency keys) before they are ever logged. Never log these values raw.
 */
public final class PiiMasker {

    private static final String FULL_MASK = "****";

    private PiiMasker() {
    }

    /** Masks all but the last 4 characters, e.g. "22212345678" -> "*******5678". */
    public static String maskKeepLast4(String value) {
        if (value == null || value.isBlank()) {
            return FULL_MASK;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return FULL_MASK;
        }
        int visibleFrom = trimmed.length() - 4;
        return "*".repeat(visibleFrom) + trimmed.substring(visibleFrom);
    }

    /** Fully redacts a value regardless of length - for passwords/raw tokens. */
    public static String fullyMask(String value) {
        return value == null ? null : FULL_MASK;
    }
}
