package dev.triode.tickets.api;

import java.util.UUID;

/** Submits new tickets. */
public interface TicketSubmission {

    /**
     * Creates a ticket in status NEW for the customer and stores it. Subject and body are validated by the domain.
     *
     * <p>With an idempotency key, the submission is safe to retry: if the customer already submitted a ticket with
     * this key and the same subject and body (exactly as sent), that ticket is returned as a replay and nothing is
     * created, also when the retries run concurrently. Keys are scoped per customer.
     *
     * @param idempotencyKey the key as sent by the client, or null for a submission without one
     * @throws dev.triode.shared.api.DomainException of kind {@code INVALID_INPUT} if subject or body is missing, blank
     *     or too long, or the key is not 1 to 255 printable ASCII characters; of kind {@code UNPROCESSABLE} if the key
     *     was already used for a different subject or body
     */
    SubmittedTicket submit(UUID customerId, String subject, String body, String idempotencyKey);
}
