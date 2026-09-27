package ng.com.nawill.pay.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.common.logging.LogFields;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Writes an {@link ErrorResponse} from servlet filters and Spring Security
 * handlers, which run before {@code GlobalExceptionHandler} can - so a
 * rejected token or signature gets the same JSON body as every other error
 * instead of an empty response.
 */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ErrorCode errorCode, Object... args) throws IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(errorCode, errorCode.message(args), MDC.get(LogFields.REQUEST_ID));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
