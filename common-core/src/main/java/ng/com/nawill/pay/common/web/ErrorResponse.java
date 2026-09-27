package ng.com.nawill.pay.common.web;

import java.time.Instant;
import java.util.List;
import ng.com.nawill.pay.common.exception.ErrorCode;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        String requestId,
        List<String> details
) {

    public static ErrorResponse of(ErrorCode errorCode, String message, String requestId) {
        return of(errorCode, message, requestId, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String requestId, List<String> details) {
        return new ErrorResponse(Instant.now(), errorCode.status().value(), errorCode.name(), message, requestId, details);
    }

    /** The code's catalogue message, for errors with no placeholders. */
    public static ErrorResponse of(ErrorCode errorCode, String requestId) {
        return of(errorCode, errorCode.message(), requestId);
    }
}
