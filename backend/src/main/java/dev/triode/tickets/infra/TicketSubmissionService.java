package dev.triode.tickets.infra;

import dev.triode.tickets.api.TicketSubmission;
import dev.triode.tickets.api.TicketView;
import dev.triode.tickets.domain.Ticket;
import dev.triode.tickets.domain.TicketRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for {@link TicketSubmission}: the domain validates and creates, the repository stores. */
@Service
class TicketSubmissionService implements TicketSubmission {

    private final TicketRepository tickets;
    private final Clock clock;

    TicketSubmissionService(TicketRepository tickets, Clock clock) {
        this.tickets = tickets;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TicketView submit(UUID customerId, String subject, String body) {
        return TicketViews.of(tickets.save(Ticket.submit(customerId, subject, body, clock)));
    }
}
