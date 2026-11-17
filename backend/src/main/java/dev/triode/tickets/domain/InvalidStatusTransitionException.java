package dev.triode.tickets.domain;

/** A ticket was asked to move to a status that its current status does not allow. */
public class InvalidStatusTransitionException extends TicketDomainException {

    private final TicketId ticketId;
    private final TicketStatus from;
    private final TicketStatus to;

    public InvalidStatusTransitionException(TicketId ticketId, TicketStatus from, TicketStatus to) {
        super("Ticket " + ticketId + ": cannot change status from " + from + " to " + to);
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
