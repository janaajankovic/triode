package dev.triode.tickets.api;

import java.util.Objects;

/**
 * Result of {@link TicketSubmission#submit}: the ticket, and whether it was created by this call or is the existing
 * ticket returned for a retry with the same idempotency key.
 *
 * @param ticket the created ticket, or for a replay the original ticket in its current state
 * @param replayed true if no ticket was created because this was a retry of an earlier submission
 */
public record SubmittedTicket(TicketView ticket, boolean replayed) {

    public SubmittedTicket {
        Objects.requireNonNull(ticket, "ticket must not be null");
    }

    public static SubmittedTicket created(TicketView ticket) {
        return new SubmittedTicket(ticket, false);
    }

    public static SubmittedTicket replayed(TicketView ticket) {
        return new SubmittedTicket(ticket, true);
    }
}
