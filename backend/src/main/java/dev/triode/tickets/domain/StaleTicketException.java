package dev.triode.tickets.domain;

/**
 * A ticket could not be saved because the stored ticket was changed or deleted after this copy was loaded
 * (optimistic locking). Reload the ticket and apply the change again.
 */
public class StaleTicketException extends TicketDomainException {

    private final TicketId ticketId;

    public StaleTicketException(TicketId ticketId) {
        this(ticketId, null);
    }

    /** With the persistence error that detected the conflict, kept as the cause for logs. */
    public StaleTicketException(TicketId ticketId, Throwable cause) {
        super("Ticket " + ticketId + " was changed or deleted by someone else", cause);
        this.ticketId = ticketId;
    }

    public TicketId getTicketId() {
        return ticketId;
    }
}
