package dev.triode.tickets.infra;

import dev.triode.tickets.api.SubmittedTicket;
import dev.triode.tickets.api.TicketSubmission;
import dev.triode.tickets.api.TicketView;
import dev.triode.tickets.domain.DuplicateIdempotencyKeyException;
import dev.triode.tickets.domain.IdempotencyKey;
import dev.triode.tickets.domain.IdempotencyKeyReusedException;
import dev.triode.tickets.domain.Ticket;
import dev.triode.tickets.domain.TicketRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Application service for {@link TicketSubmission}: the domain validates and creates, the repository stores.
 *
 * <p>Idempotent submissions (TRI-18) need transactions controlled step by step, so this class uses
 * {@link TransactionTemplate}s instead of {@code @Transactional}. With a key:
 *
 * <ol>
 *   <li>look up the customer's ticket with that key (read transaction); if found, replay it when it is the same
 *       request, otherwise reject the reused key;
 *   <li>otherwise insert the new ticket in its own transaction;
 *   <li>if the insert loses against a concurrent submission with the same key, PostgreSQL first waits for the other
 *       transaction and then fails the insert on the unique constraint. That failure aborts the whole transaction,
 *       so the winner is loaded in a <em>new</em> transaction and step 1's rule is applied to it.
 * </ol>
 *
 * <p>The database constraint alone prevents duplicates; there are no locks in Java. The lookup in step 1 only spares
 * sequential retries a failed insert. Both templates use {@code REQUIRES_NEW}, so every step really runs in its own
 * transaction even if a caller ever wraps {@code submit} in one.
 */
@Service
class TicketSubmissionService implements TicketSubmission {

    private static final Logger log = LoggerFactory.getLogger(TicketSubmissionService.class);

    private final TicketRepository tickets;
    private final Clock clock;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;

    TicketSubmissionService(TicketRepository tickets, Clock clock, PlatformTransactionManager transactionManager) {
        this.tickets = tickets;
        this.clock = clock;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction.setReadOnly(true);
    }

    @Override
    public SubmittedTicket submit(UUID customerId, String subject, String body, String idempotencyKey) {
        if (idempotencyKey == null) {
            return SubmittedTicket.created(save(Ticket.submit(customerId, subject, body, clock)));
        }
        IdempotencyKey key = new IdempotencyKey(idempotencyKey);
        // Validates subject and body before any database access: invalid content is a 400 even for a known key.
        Ticket candidate = Ticket.submit(customerId, subject, body, key, clock);

        Optional<Ticket> existing = findByKey(customerId, key);
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), candidate);
        }
        try {
            return SubmittedTicket.created(save(candidate));
        } catch (DuplicateIdempotencyKeyException e) {
            // The failed insert's transaction is rolled back by now; read the winner in a new one.
            Ticket winner = findByKey(customerId, key)
                    .orElseThrow(() -> new IllegalStateException(
                            "the ticket holding the idempotency key is missing (tickets are never deleted)", e));
            log.debug("Concurrent submission with the same idempotency key won; ticket {}", winner.getId());
            return replayOrReject(winner, candidate);
        }
    }

    private TicketView save(Ticket ticket) {
        Ticket saved = writeTransaction.execute(status -> tickets.save(ticket));
        return TicketViews.of(Objects.requireNonNull(saved, "repository returned no ticket"));
    }

    private Optional<Ticket> findByKey(UUID customerId, IdempotencyKey key) {
        Optional<Ticket> found = readTransaction.execute(status -> tickets.findByIdempotencyKey(customerId, key));
        return Objects.requireNonNull(found, "repository returned no result");
    }

    private static SubmittedTicket replayOrReject(Ticket stored, Ticket candidate) {
        if (!stored.isSameRequestAs(candidate)) {
            throw new IdempotencyKeyReusedException();
        }
        return SubmittedTicket.replayed(TicketViews.of(stored));
    }
}
