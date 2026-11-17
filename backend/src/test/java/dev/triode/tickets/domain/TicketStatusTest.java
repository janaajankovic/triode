package dev.triode.tickets.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TicketStatusTest {

    /** The full transition table, written out by hand: 6 × 6 = 36 rows, 8 of them allowed. */
    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource(textBlock = """
            NEW,                 NEW,                 false
            NEW,                 TRIAGING,            true
            NEW,                 NEEDS_MANUAL_TRIAGE, false
            NEW,                 PENDING_REVIEW,      false
            NEW,                 RESOLVED,            false
            NEW,                 ESCALATED,           false
            TRIAGING,            NEW,                 false
            TRIAGING,            TRIAGING,            false
            TRIAGING,            NEEDS_MANUAL_TRIAGE, true
            TRIAGING,            PENDING_REVIEW,      true
            TRIAGING,            RESOLVED,            false
            TRIAGING,            ESCALATED,           false
            NEEDS_MANUAL_TRIAGE, NEW,                 false
            NEEDS_MANUAL_TRIAGE, TRIAGING,            false
            NEEDS_MANUAL_TRIAGE, NEEDS_MANUAL_TRIAGE, false
            NEEDS_MANUAL_TRIAGE, PENDING_REVIEW,      true
            NEEDS_MANUAL_TRIAGE, RESOLVED,            false
            NEEDS_MANUAL_TRIAGE, ESCALATED,           true
            PENDING_REVIEW,      NEW,                 false
            PENDING_REVIEW,      TRIAGING,            false
            PENDING_REVIEW,      NEEDS_MANUAL_TRIAGE, false
            PENDING_REVIEW,      PENDING_REVIEW,      false
            PENDING_REVIEW,      RESOLVED,            true
            PENDING_REVIEW,      ESCALATED,           true
            RESOLVED,            NEW,                 false
            RESOLVED,            TRIAGING,            false
            RESOLVED,            NEEDS_MANUAL_TRIAGE, false
            RESOLVED,            PENDING_REVIEW,      false
            RESOLVED,            RESOLVED,            false
            RESOLVED,            ESCALATED,           false
            ESCALATED,           NEW,                 false
            ESCALATED,           TRIAGING,            false
            ESCALATED,           NEEDS_MANUAL_TRIAGE, false
            ESCALATED,           PENDING_REVIEW,      false
            ESCALATED,           RESOLVED,            false
            ESCALATED,           ESCALATED,           false
            """)
    void canTransitionTo(TicketStatus from, TicketStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @Test
    void transitionTableAboveCoversEveryPair() {
        // If a status is added, the 36-row table above is no longer complete and must be extended.
        assertThat(TicketStatus.values()).hasSize(6);
    }

    @Test
    void canTransitionToRejectsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> TicketStatus.NEW.canTransitionTo(null))
                .withMessageContaining("next status");
    }
}
