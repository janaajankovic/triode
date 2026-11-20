package dev.triode.shared.infra.web;

import dev.triode.shared.api.DomainException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * The one global exception handler (ADR 0010). Every error leaves the API as an RFC 9457 problem detail
 * ({@code application/problem+json}); field-level reasons go in the {@value #ERRORS} extension member.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} keeps Spring MVC's own exceptions (405, 415, unknown route, ...)
 * on their correct status; only what nothing else handles becomes a 500. Responses never contain stack traces or
 * messages of unexpected exceptions; those are logged here.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String ERRORS = "errors";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final long maxRequestBodyBytes;

    GlobalExceptionHandler(@Value("${triode.api.max-request-body-bytes}") long maxRequestBodyBytes) {
        this.maxRequestBodyBytes = maxRequestBodyBytes;
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomainException(DomainException e) {
        HttpStatus status = switch (e.kind()) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case CONFLICT -> HttpStatus.CONFLICT;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        // Domain messages are client-safe by contract (see DomainException); the class name is enough for logs.
        log.debug("Domain rule broken: {} ({})", e.getClass().getSimpleName(), e.kind());
        return respond(status, e.getMessage(), e.details());
    }

    @ExceptionHandler(InvalidCustomerIdException.class)
    ResponseEntity<ProblemDetail> handleInvalidCustomerId(InvalidCustomerIdException e) {
        return respond(
                HttpStatus.BAD_REQUEST,
                "Invalid " + HeaderCurrentCustomer.HEADER + " header",
                Map.of(HeaderCurrentCustomer.HEADER, e.getReason()));
    }

    /** Raised by {@link RequestBodySizeFilter} when the declared Content-Length is over the limit. */
    @ExceptionHandler(RequestBodyTooLargeException.class)
    ResponseEntity<ProblemDetail> handleRequestBodyTooLarge(RequestBodyTooLargeException e) {
        return respond(HttpStatus.CONTENT_TOO_LARGE, tooLargeDetail(), Map.of());
    }

    /** Malformed JSON, unknown properties, wrong types, and the Jackson document-length limit for chunked bodies. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (findCause(e, StreamConstraintsException.class) != null) {
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                    .body(problem(HttpStatus.CONTENT_TOO_LARGE, tooLargeDetail(), Map.of()));
        }
        UnrecognizedPropertyException unknown = findCause(e, UnrecognizedPropertyException.class);
        if (unknown != null) {
            return ResponseEntity.badRequest()
                    .body(problem(
                            HttpStatus.BAD_REQUEST,
                            "Request body contains unknown properties",
                            Map.of(unknown.getPropertyName(), "unknown property")));
        }
        // The parser's own message names internal classes and echoes input, so it is not passed on.
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "Request body is not valid JSON for this endpoint", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", Map.of());
    }

    private String tooLargeDetail() {
        return "Request body must not exceed " + maxRequestBodyBytes + " bytes";
    }

    private static ResponseEntity<ProblemDetail> respond(HttpStatus status, String detail, Map<String, String> errors) {
        return ResponseEntity.status(status).body(problem(status, detail, errors));
    }

    private static ProblemDetail problem(HttpStatus status, String detail, Map<String, String> errors) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        if (!errors.isEmpty()) {
            body.setProperty(ERRORS, errors);
        }
        return body;
    }

    private static <T extends Throwable> T findCause(Throwable e, Class<T> type) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
        }
        return null;
    }
}
