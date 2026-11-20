package dev.triode.tickets.api;

import java.util.UUID;

/** Submits new tickets. */
public interface TicketSubmission {

    /**
     * Creates a ticket in status NEW for the customer and stores it. Subject and body are validated by the domain.
     *
     * @throws dev.triode.shared.api.DomainException of kind {@code INVALID_INPUT} if subject or body is missing, blank
     *     or too long
     */
    TicketView submit(UUID customerId, String subject, String body);
}
