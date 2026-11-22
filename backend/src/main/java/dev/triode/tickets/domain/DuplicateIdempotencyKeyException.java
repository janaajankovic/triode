package dev.triode.tickets.domain;

import java.io.Serial;

/**
 * Thrown by {@link TicketRepository#save(Ticket)} when another ticket of the same customer already has the new
 * ticket's idempotency key, usually because a concurrent retry committed first. The storage transaction is lost; the
 * caller loads the stored ticket in a new transaction and decides between replay and rejection.
 *
 * <p>Not a {@code DomainException}: it is a signal between the repository and the submission service, never an API
 * error. If it ever escaped, the API would report a 500.
 */
public class DuplicateIdempotencyKeyException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicateIdempotencyKeyException(Throwable cause) {
        super("Another ticket of this customer already has this idempotency key", cause);
    }
}
