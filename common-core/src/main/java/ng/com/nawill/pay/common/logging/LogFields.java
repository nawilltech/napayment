package ng.com.nawill.pay.common.logging;

/** MDC keys used across every module for structured, correlated logging. */
public final class LogFields {

    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    public static final String MODULE = "module";

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private LogFields() {
    }
}
