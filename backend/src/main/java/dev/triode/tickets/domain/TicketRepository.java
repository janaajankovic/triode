package dev.triode.tickets.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for storing tickets. The domain defines what it needs; the persistence adapter in the module's infra package
 * implements it.
 */
public interface TicketRepository {

    /**
     * Saves a new ticket (version null) or the changes to a loaded one, and returns the saved ticket with its new
     * version.
     *
     * <p>The argument is left unchanged, so callers must continue with the returned ticket. Saving the same new
     * ticket a second time fails, because its id is already stored.
     *
     * @throws StaleTicketException if the stored ticket was changed since this one was loaded (its version is stale)
     *     or no longer exists; the newer state is never overwritten
     * @throws DuplicateIdempotencyKeyException if the new ticket's customer already has a ticket with its idempotency
     *     key (the storage's uniqueness rule decides, also between concurrent saves); the current transaction is then
     *     unusable, so the stored ticket must be loaded in a new one
     */
    Ticket save(Ticket ticket);

    /**
     * Loads the customer's ticket that was submitted with this idempotency key. Keys are scoped per customer, so
     * another customer's ticket with the same key is never returned.
     *
     * @throws RuntimeException (not a {@link TicketDomainException}) if the stored data is invalid, as for
     *     {@link #findById(TicketId)}
     */
    Optional<Ticket> findByIdempotencyKey(UUID customerId, IdempotencyKey key);

    /**
     * Loads a ticket by id.
     *
     * @throws RuntimeException (not a {@link TicketDomainException}) if the stored data breaks the ticket's field
     *     rules: a server-side fault, never reported to the caller as invalid input. The
     *     {@link InvalidTicketException} is the root cause.
     */
    Optional<Ticket> findById(TicketId id);
}
