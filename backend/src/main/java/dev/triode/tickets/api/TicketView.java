package dev.triode.tickets.api;

import java.time.Instant;
import java.util.UUID;

/**
 * A ticket as other modules and API clients see it; {@code POST} and {@code GET} return the same shape. {@code status}
 * is the status name (for example {@code NEW}); the status enum itself is internal to the tickets module.
 *
 * <p>{@code subject} and {@code body} are customer text (personal data): never log them, and only return them to the
 * ticket's own customer.
 */
public record TicketView(UUID id, String subject, String body, String status, Instant createdAt) {}
