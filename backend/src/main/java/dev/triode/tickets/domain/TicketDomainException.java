package dev.triode.tickets.domain;

import java.io.Serial;

/** Base class for business rule violations in the tickets domain. */
public abstract class TicketDomainException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    protected TicketDomainException(String message) {
        super(message);
    }

    protected TicketDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
