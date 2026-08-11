package ng.com.nawill.pay.common.web;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        String requestId,
        List<String> details
) {

    public static ErrorResponse of(int status, String errorCode, String message, String requestId) {
        return new ErrorResponse(Instant.now(), status, errorCode, message, requestId, List.of());
    }

    public static ErrorResponse of(int status, String errorCode, String message, String requestId, List<String> details) {
        return new ErrorResponse(Instant.now(), status, errorCode, message, requestId, details);
    }
}
