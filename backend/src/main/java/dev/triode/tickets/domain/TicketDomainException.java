package dev.triode.tickets.domain;

/** Base class for business rule violations in the tickets domain. */
public abstract class TicketDomainException extends RuntimeException {

    protected TicketDomainException(String message) {
        super(message);
    }
}
