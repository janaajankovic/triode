package dev.triode.tickets.domain;

import java.util.Optional;

/**
 * Port for storing tickets. The domain defines what it needs; the persistence adapter in the module's infra package
 * implements it.
 */
public interface TicketRepository {

    /**
     * Saves a new ticket (version null) or the changes to a loaded one, and returns the saved ticket with its new
     * version. The argument is left unchanged, so callers continue with the returned ticket.
     *
     * <p>If the stored ticket has changed since this one was loaded (its version is stale) or no longer exists, the
     * save fails with Spring's optimistic locking exception instead of overwriting the newer state.
     */
    Ticket save(Ticket ticket);

    /**
     * Loads a ticket by id.
     *
     * @throws InvalidTicketException if the stored data breaks the ticket's field rules
     */
    Optional<Ticket> findById(TicketId id);
}
