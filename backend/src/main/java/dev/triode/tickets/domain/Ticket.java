package dev.triode.tickets.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root of the tickets module: a customer's support request and its status. Status changes go through the
 * business methods, which only allow the transitions defined by {@link TicketStatus#canTransitionTo(TicketStatus)}.
 */
public final class Ticket {

    /** Maximum subject length in Unicode code points (same as PostgreSQL character counting). */
    public static final int MAX_SUBJECT_LENGTH = 255;

    /** Maximum body length in Unicode code points (same as PostgreSQL character counting). */
    public static final int MAX_BODY_LENGTH = 10_000;

    private final TicketId id;
    private final UUID customerId;
    private final String subject;
    private final String body;
    private TicketStatus status;
    private final Instant createdAt;

    private Ticket(TicketId id, UUID customerId, String subject, String body, TicketStatus status, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.subject = subject;
        this.body = body;
        this.status = status;
        this.createdAt = createdAt;
    }

    /**
     * Creates a new ticket in status NEW. Subject and body are stripped of surrounding whitespace, must not be blank
     * and must fit {@link #MAX_SUBJECT_LENGTH} and {@link #MAX_BODY_LENGTH}.
     *
     * @throws NullPointerException if any argument is null
     * @throws InvalidTicketException if subject or body is blank or too long
     */
    public static Ticket submit(UUID customerId, String subject, String body, Clock clock) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        return new Ticket(
                TicketId.generate(),
                customerId,
                requireText("subject", subject, MAX_SUBJECT_LENGTH),
                requireText("body", body, MAX_BODY_LENGTH),
                TicketStatus.NEW,
                clock.instant());
    }

    private static String requireText(String field, String value, int maxLength) {
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new InvalidTicketException(field, "must not be blank");
        }
        if (stripped.codePointCount(0, stripped.length()) > maxLength) {
            throw new InvalidTicketException(field, "must be at most " + maxLength + " characters");
        }
        return stripped;
    }

    /** NEW → TRIAGING. */
    public void startTriage() {
        transitionTo(TicketStatus.TRIAGING);
    }

    /** TRIAGING or NEEDS_MANUAL_TRIAGE → PENDING_REVIEW. */
    public void completeTriage() {
        transitionTo(TicketStatus.PENDING_REVIEW);
    }

    /** TRIAGING → NEEDS_MANUAL_TRIAGE. */
    public void requestManualTriage() {
        transitionTo(TicketStatus.NEEDS_MANUAL_TRIAGE);
    }

    /** PENDING_REVIEW → RESOLVED. */
    public void resolve() {
        transitionTo(TicketStatus.RESOLVED);
    }

    /** NEEDS_MANUAL_TRIAGE or PENDING_REVIEW → ESCALATED. */
    public void escalate() {
        transitionTo(TicketStatus.ESCALATED);
    }

    private void transitionTo(TicketStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(id, status, target);
        }
        status = target;
    }

    public TicketId getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Ticket ticket && id.equals(ticket.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Leaves out subject and body: they are customer text and must not end up in logs. */
    @Override
    public String toString() {
        return "Ticket[id=" + id + ", status=" + status + ", createdAt=" + createdAt + "]";
    }
}
