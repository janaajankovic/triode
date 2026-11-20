package dev.triode.tickets.domain;

import java.io.Serial;

/** A ticket was asked to move to a status that its current status does not allow. Kind {@code CONFLICT}. */
public class InvalidStatusTransitionException extends TicketDomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final TicketId ticketId;
    private final TicketStatus from;
    private final TicketStatus to;

    public InvalidStatusTransitionException(TicketId ticketId, TicketStatus from, TicketStatus to) {
        super(Kind.CONFLICT, "Ticket " + ticketId + ": cannot change status from " + from + " to " + to);
        this.ticketId = ticketId;
        this.from = from;
        this.to = to;
    }

    public TicketId getTicketId() {
        return ticketId;
    }

    public TicketStatus getFrom() {
        return from;
    }

    public TicketStatus getTo() {
        return to;
    }
}
