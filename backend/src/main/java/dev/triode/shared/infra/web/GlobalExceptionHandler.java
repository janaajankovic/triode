package dev.triode.shared.infra.web;

import dev.triode.shared.api.DomainException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
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

    private static final String UNKNOWN_PROPERTY = "unknown property";
    private static final Pattern SAFE_PROPERTY_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

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
            // The name is client input: echo it only if it looks like a plain identifier, never arbitrary text.
            String name = unknown.getPropertyName();
            Map<String, String> errors =
                    name != null && SAFE_PROPERTY_NAME.matcher(name).matches()
                            ? Map.of(name, UNKNOWN_PROPERTY)
                            : Map.of();
            return ResponseEntity.badRequest()
                    .body(problem(HttpStatus.BAD_REQUEST, "Request body contains unknown properties", errors));
        }
        // The parser's own message names internal classes and echoes input, so it is not passed on.
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "Request body is not valid JSON for this endpoint", Map.of()));
    }

    /**
     * A path variable or request parameter that cannot be converted, for example a non-UUID ticket id. Spring's own
     * detail echoes the rejected value, which is client input, so only the parameter name (from our code) is named.
     * {@code instance} would default to the raw request path, which holds the rejected value too, so it is set to the
     * matched route template instead (for example {@code /api/v1/tickets/%7Bid%7D}, the encoded {@code {id}}).
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name =
                e instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : e.getPropertyName();
        String reason = UUID.class.equals(e.getRequiredType()) ? "must be a UUID" : "has an invalid value";
        Map<String, String> errors = name != null ? Map.of(name, reason) : Map.of();
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Invalid request parameter", errors);
        if (request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST)
                instanceof String route) {
            body.setInstance(routeUri(route));
        }
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", Map.of());
    }

    /**
     * No controller route or static resource matches the path. Spring's own detail names the path, and the default
     * {@code instance} is the raw request path; both are client input. The detail is generic and {@code instance} is an
     * opaque occurrence id, logged at debug level with the method (not the path) so a report can be matched to the log.
     */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        URI occurrence = URI.create("urn:uuid:" + UUID.randomUUID());
        log.debug("No route for {} request ({})", e.getHttpMethod(), occurrence);
        ProblemDetail body = problem(HttpStatus.NOT_FOUND, "No endpoint matches this request", Map.of());
        body.setInstance(occurrence);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /** A route template as a URI; the braces of its variables are percent-encoded ({@code {id}} → {@code %7Bid%7D}). */
    private static URI routeUri(String route) {
        try {
            return new URI(null, null, route, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Route template is not a valid path: " + route, e);
        }
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
