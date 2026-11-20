package dev.triode.tickets.domain;

import dev.triode.shared.api.DomainException;
import java.io.Serial;
import java.util.Map;

/** Base class for business rule violations in the tickets domain. */
public abstract class TicketDomainException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    protected TicketDomainException(Kind kind, String message) {
        super(kind, message);
    }

    protected TicketDomainException(Kind kind, String message, Map<String, String> details) {
        super(kind, message, details);
    }

    protected TicketDomainException(Kind kind, String message, Throwable cause) {
        super(kind, message, cause);
    }
}
