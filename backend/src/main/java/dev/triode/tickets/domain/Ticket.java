package dev.triode.tickets.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final Long version;

    private Ticket(
            TicketId id,
            UUID customerId,
            String subject,
            String body,
            TicketStatus status,
            Instant createdAt,
            Long version) {
        this.id = id;
        this.customerId = customerId;
        this.subject = subject;
        this.body = body;
        this.status = status;
        this.createdAt = createdAt;
        this.version = version;
    }

    /**
     * Creates a new ticket in status NEW. Subject and body are stripped of surrounding whitespace, must not be blank
     * and must fit {@link #MAX_SUBJECT_LENGTH} and {@link #MAX_BODY_LENGTH}. {@code createdAt} is truncated to
     * microseconds, the precision PostgreSQL stores, so a saved ticket loads back with the same value.
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
                checkText("subject", subject.strip(), MAX_SUBJECT_LENGTH),
                checkText("body", body.strip(), MAX_BODY_LENGTH),
                TicketStatus.NEW,
                clock.instant().truncatedTo(ChronoUnit.MICROS),
                null);
    }

    /**
     * Rebuilds a ticket from persisted state. Only for loading saved tickets; new tickets are created with
     * {@link #submit(UUID, String, String, Clock)}.
     *
     * <p>Accepts any status and changes no value. The field rules are checked again: stored subject and body must
     * already be stripped, non-blank and within their limits.
     *
     * @throws InvalidTicketException if any value is null or breaks a field rule (invalid stored data)
     */
    public static Ticket reconstitute(
            TicketId id,
            UUID customerId,
            String subject,
            String body,
            TicketStatus status,
            Instant createdAt,
            long version) {
        requireStored("id", id);
        requireStored("customerId", customerId);
        requireStored("subject", subject);
        requireStored("body", body);
        requireStored("status", status);
        requireStored("createdAt", createdAt);
        return new Ticket(
                id,
                customerId,
                checkStoredText("subject", subject, MAX_SUBJECT_LENGTH),
                checkStoredText("body", body, MAX_BODY_LENGTH),
                status,
                createdAt,
                version);
    }

    private static void requireStored(String field, Object value) {
        if (value == null) {
            throw new InvalidTicketException(field, "must not be null");
        }
    }

    private static String checkStoredText(String field, String value, int maxLength) {
        if (value.isBlank()) {
            throw new InvalidTicketException(field, "must not be blank");
        }
        if (!value.equals(value.strip())) {
            throw new InvalidTicketException(field, "must not have leading or trailing whitespace");
        }
        return checkText(field, value, maxLength);
    }

    /** Checks an already stripped value against the blank and length rules. */
    private static String checkText(String field, String value, int maxLength) {
        if (value.isEmpty()) {
            throw new InvalidTicketException(field, "must not be blank");
        }
        if (value.codePointCount(0, value.length()) > maxLength) {
            throw new InvalidTicketException(field, "must be at most " + maxLength + " characters");
        }
        return value;
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

    /**
     * Version of the stored row this ticket was loaded from or saved as, used for optimistic locking; null for a
     * ticket that was never saved.
     */
    public Long getVersion() {
        return version;
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
