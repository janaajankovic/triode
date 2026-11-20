package dev.triode.tickets.domain;

import java.io.Serial;

/**
 * A ticket could not be saved because the stored ticket was changed or deleted after this copy was loaded
 * (optimistic locking). Reload the ticket and apply the change again. Kind {@code CONFLICT}.
 */
public class StaleTicketException extends TicketDomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final TicketId ticketId;

    public StaleTicketException(TicketId ticketId) {
        this(ticketId, null);
    }

    /** With the persistence error that detected the conflict, kept as the cause for logs. */
    public StaleTicketException(TicketId ticketId, Throwable cause) {
        super(Kind.CONFLICT, "Ticket " + ticketId + " was changed or deleted by someone else", cause);
        this.ticketId = ticketId;
    }

    public TicketId getTicketId() {
        return ticketId;
    }
}
