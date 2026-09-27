package ng.com.nawill.pay.common.exception;

/**
 * The one exception type for an expected API error, translated into an
 * {@link ng.com.nawill.pay.common.web.ErrorResponse} by
 * {@link GlobalExceptionHandler}. Status, code and message all come from the
 * {@link ErrorCode} catalogue; {@code args} fill its message placeholders.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, Object... args) {
        super(errorCode.message(args));
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
