package dev.triode.tickets.domain;

import java.util.Objects;
import java.util.UUID;

/** Identity of a {@link Ticket}. */
public record TicketId(UUID value) {

    public TicketId {
        Objects.requireNonNull(value, "ticket id must not be null");
    }

    public static TicketId generate() {
        return new TicketId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
