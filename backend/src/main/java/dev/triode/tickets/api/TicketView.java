package dev.triode.tickets.api;

import java.time.Instant;
import java.util.UUID;

/**
 * A submitted ticket as other modules and API clients see it. {@code status} is the status name (for example
 * {@code NEW}); the status enum itself is internal to the tickets module.
 */
public record TicketView(UUID id, String status, Instant createdAt) {}
