package dev.triode.tickets.domain;

import java.util.Objects;

/** Lifecycle of a ticket. The allowed transitions are defined in {@link #canTransitionTo(TicketStatus)}. */
public enum TicketStatus {
    NEW,
    TRIAGING,
    NEEDS_MANUAL_TRIAGE,
    PENDING_REVIEW,
    RESOLVED,
    ESCALATED;

    /**
     * Whether a ticket in this status may move to {@code next}. RESOLVED and ESCALATED are final.
     *
     * @throws NullPointerException if {@code next} is null
     */
    public boolean canTransitionTo(TicketStatus next) {
        Objects.requireNonNull(next, "next status must not be null");
        return switch (this) {
            case NEW -> next == TRIAGING;
            case TRIAGING -> next == PENDING_REVIEW || next == NEEDS_MANUAL_TRIAGE;
            case NEEDS_MANUAL_TRIAGE -> next == PENDING_REVIEW || next == ESCALATED;
            case PENDING_REVIEW -> next == RESOLVED || next == ESCALATED;
            case RESOLVED, ESCALATED -> false;
        };
    }
}
