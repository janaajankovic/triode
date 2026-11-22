package dev.triode.shared.api;

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base class for business rule violations of every module. Pure Java, so domain packages can extend it.
 *
 * <p>The {@link Kind} says what went wrong in terms a caller understands; the global exception handler in the shared
 * module turns it into an HTTP status without knowing the concrete exception (ADR 0010).
 *
 * <p>The message and the details are sent to API clients. They must describe the rule that was broken and must never
 * contain customer text (subject, body) or other personal data.
 */
public abstract class DomainException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** What kind of rule was broken. */
    public enum Kind {
        /** The caller sent a value the domain does not accept. */
        INVALID_INPUT,
        /** The request does not fit the current state, for example a forbidden status change or a stale version. */
        CONFLICT,
        /** The requested object does not exist. */
        NOT_FOUND,
        /**
         * The request is well-formed and understood, but cannot be processed as sent, for example an idempotency key
         * reused for a different request.
         */
        UNPROCESSABLE
    }

    private final Kind kind;

    // LinkedHashMap (not Map): keeps insertion order and is Serializable, as every Throwable must be.
    private final LinkedHashMap<String, String> details;

    protected DomainException(Kind kind, String message) {
        this(kind, message, Map.of(), null);
    }

    protected DomainException(Kind kind, String message, Map<String, String> details) {
        this(kind, message, details, null);
    }

    protected DomainException(Kind kind, String message, Throwable cause) {
        this(kind, message, Map.of(), cause);
    }

    private DomainException(Kind kind, String message, Map<String, String> details, Throwable cause) {
        super(message, cause);
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(details, "details must not be null");
        this.details = new LinkedHashMap<>();
        details.forEach((key, value) -> this.details.put(
                Objects.requireNonNull(key, "detail key must not be null"),
                Objects.requireNonNull(value, "detail value must not be null")));
    }

    public Kind kind() {
        return kind;
    }

    /** Machine-readable details, for example field name to reason; empty if there are none. Unmodifiable. */
    public Map<String, String> details() {
        return Collections.unmodifiableMap(details);
    }
}
