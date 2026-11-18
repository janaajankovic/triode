package dev.triode.tickets.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a {@link Ticket}. Serializable so that domain exceptions which carry it stay serializable, as every
 * {@link Throwable} is.
 */
public record TicketId(UUID value) implements Serializable {

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
