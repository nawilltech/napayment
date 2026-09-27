package ng.com.nawill.pay.common.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import ng.com.nawill.pay.common.logging.LogFields;
import ng.com.nawill.pay.common.web.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Every error response is built from the {@link ErrorCode} catalogue - no inline codes or messages. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Per-field detail suffixes for request-binding failures ("bankId: invalid value"). */
    private static final String DETAIL_INVALID_VALUE = "invalid value";
    private static final String DETAIL_REQUIRED = "is required";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        log.warn("handled api exception: errorCode={} message={}", ex.getErrorCode(), ex.getMessage());
        return respond(ex.getErrorCode(), ex.getMessage(), List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("permission denied: message={}", ex.getMessage());
        return respond(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        log.warn("authentication failed: message={}", ex.getMessage());
        return respond(ErrorCode.UNAUTHENTICATED);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        log.warn("validation failed: details={}", details);
        return respond(ErrorCode.VALIDATION_ERROR, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .toList();
        log.warn("constraint violation: details={}", details);
        return respond(ErrorCode.VALIDATION_ERROR, details);
    }

    /** Unparseable body, e.g. a malformed UUID or enum value inside the JSON. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("unreadable request body: {}", ex.getMostSpecificCause().getMessage());
        return respond(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = ex.getName() + ": " + DETAIL_INVALID_VALUE;
        log.warn("argument type mismatch: {}", detail);
        return respond(ErrorCode.VALIDATION_ERROR, List.of(detail));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        String detail = ex.getParameterName() + ": " + DETAIL_REQUIRED;
        log.warn("missing request parameter: {}", detail);
        return respond(ErrorCode.VALIDATION_ERROR, List.of(detail));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoRoute(NoResourceFoundException ex) {
        log.warn("no route: {}", ex.getResourcePath());
        return respond(ErrorCode.ROUTE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("method not supported: {}", ex.getMethod());
        return respond(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("media type not supported: {}", ex.getContentType());
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("unhandled exception", ex);
        return respond(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode errorCode) {
        return respond(errorCode, List.of());
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode errorCode, List<String> details) {
        return respond(errorCode, errorCode.message(), details);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode errorCode, String message, List<String> details) {
        return ResponseEntity.status(errorCode.status())
                .body(ErrorResponse.of(errorCode, message, MDC.get(LogFields.REQUEST_ID), details));
    }
}
