package dev.triode.tickets.infra;

import dev.triode.tickets.api.TicketView;
import dev.triode.tickets.domain.Ticket;

/** The one mapping from the domain {@link Ticket} to the published {@link TicketView}. */
final class TicketViews {

    private TicketViews() {}

    static TicketView of(Ticket ticket) {
        return new TicketView(
                ticket.getId().value(),
                ticket.getSubject(),
                ticket.getBody(),
                ticket.getStatus().name(),
                ticket.getCreatedAt());
    }
}
