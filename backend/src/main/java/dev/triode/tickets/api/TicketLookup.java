package dev.triode.tickets.api;

import java.util.UUID;

/** Reads tickets on behalf of a customer. */
public interface TicketLookup {

    /**
     * Returns the customer's own ticket.
     *
     * @throws dev.triode.shared.api.DomainException of kind {@code NOT_FOUND} if no ticket has this id <em>or</em> it
     *     belongs to another customer. Both cases throw the same exception with the same message, so a caller cannot
     *     tell which ids exist (IDOR protection).
     */
    TicketView findForCustomer(UUID ticketId, UUID customerId);
}
