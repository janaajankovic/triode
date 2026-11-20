package dev.triode.tickets.domain;

import java.io.Serial;

/**
 * The requested ticket does not exist for this caller. Kind {@code NOT_FOUND}.
 *
 * <p>Thrown both when no ticket has the id and when it belongs to another customer, with the same message, so the
 * response reveals nothing about which ids exist. That is why it only takes the requested id: it cannot name the owner.
 */
public class TicketNotFoundException extends TicketDomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final TicketId ticketId;

    public TicketNotFoundException(TicketId ticketId) {
        super(Kind.NOT_FOUND, "Ticket " + ticketId + " not found");
        this.ticketId = ticketId;
    }

    public TicketId getTicketId() {
        return ticketId;
    }
}
