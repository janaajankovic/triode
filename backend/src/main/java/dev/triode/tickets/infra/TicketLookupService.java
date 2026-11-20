package dev.triode.tickets.infra;

import dev.triode.tickets.api.TicketLookup;
import dev.triode.tickets.api.TicketView;
import dev.triode.tickets.domain.TicketId;
import dev.triode.tickets.domain.TicketNotFoundException;
import dev.triode.tickets.domain.TicketRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for {@link TicketLookup}. An unknown id and another customer's ticket go through the same path
 * and end in the same {@link TicketNotFoundException}.
 */
@Service
class TicketLookupService implements TicketLookup {

    private final TicketRepository tickets;

    TicketLookupService(TicketRepository tickets) {
        this.tickets = tickets;
    }

    @Override
    @Transactional(readOnly = true)
    public TicketView findForCustomer(UUID ticketId, UUID customerId) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        TicketId id = new TicketId(ticketId);
        return tickets.findById(id)
                .filter(ticket -> ticket.getCustomerId().equals(customerId))
                .map(TicketViews::of)
                .orElseThrow(() -> new TicketNotFoundException(id));
    }
}
