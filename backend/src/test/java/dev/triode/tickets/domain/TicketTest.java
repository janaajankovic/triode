package dev.triode.tickets.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TicketTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID CUSTOMER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final String SUBJECT = "Cannot log in";
    private static final String BODY = "The login page shows an error after I enter my password.";

    private static Ticket newTicket() {
        return Ticket.submit(CUSTOMER, SUBJECT, BODY, CLOCK);
    }

    /** A fresh ticket moved to {@code status} along legal calls only. */
    private static Ticket ticketIn(TicketStatus status) {
        Ticket ticket = newTicket();
        switch (status) {
            case NEW -> {}
            case TRIAGING -> ticket.startTriage();
            case NEEDS_MANUAL_TRIAGE -> {
                ticket.startTriage();
                ticket.requestManualTriage();
            }
            case PENDING_REVIEW -> {
                ticket.startTriage();
                ticket.completeTriage();
            }
            case RESOLVED -> {
                ticket.startTriage();
                ticket.completeTriage();
                ticket.resolve();
            }
            case ESCALATED -> {
                ticket.startTriage();
                ticket.completeTriage();
                ticket.escalate();
            }
        }
        assertThat(ticket.getStatus()).isEqualTo(status);
        return ticket;
    }

    @Nested
    class Submit {

        @Test
        void createsNewTicket() {
            Ticket ticket = newTicket();

            assertThat(ticket.getId()).isNotNull();
            assertThat(ticket.getCustomerId()).isEqualTo(CUSTOMER);
            assertThat(ticket.getSubject()).isEqualTo(SUBJECT);
            assertThat(ticket.getBody()).isEqualTo(BODY);
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEW);
            assertThat(ticket.getCreatedAt()).isEqualTo(NOW);
        }

        @Test
        void trimsSubjectAndBody() {
            Ticket ticket = Ticket.submit(CUSTOMER, "  Cannot log in \n", "\t Body text  ", CLOCK);

            assertThat(ticket.getSubject()).isEqualTo("Cannot log in");
            assertThat(ticket.getBody()).isEqualTo("Body text");
        }

        @Test
        void rejectsNullArguments() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Ticket.submit(null, SUBJECT, BODY, CLOCK))
                    .withMessageContaining("customerId");
            assertThatNullPointerException()
                    .isThrownBy(() -> Ticket.submit(CUSTOMER, null, BODY, CLOCK))
                    .withMessageContaining("subject");
            assertThatNullPointerException()
                    .isThrownBy(() -> Ticket.submit(CUSTOMER, SUBJECT, null, CLOCK))
                    .withMessageContaining("body");
            assertThatNullPointerException()
                    .isThrownBy(() -> Ticket.submit(CUSTOMER, SUBJECT, BODY, null))
                    .withMessageContaining("clock");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t\n"})
        void rejectsBlankSubject(String subject) {
            assertInvalid(() -> Ticket.submit(CUSTOMER, subject, BODY, CLOCK), "subject", "must not be blank");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t\n"})
        void rejectsBlankBody(String body) {
            assertInvalid(() -> Ticket.submit(CUSTOMER, SUBJECT, body, CLOCK), "body", "must not be blank");
        }

        @Test
        void acceptsSubjectOfMaxLength() {
            String subject = "a".repeat(Ticket.MAX_SUBJECT_LENGTH);

            assertThat(Ticket.submit(CUSTOMER, subject, BODY, CLOCK).getSubject())
                    .isEqualTo(subject);
        }

        @Test
        void rejectsSubjectOverMaxLength() {
            String subject = "a".repeat(Ticket.MAX_SUBJECT_LENGTH + 1);

            assertInvalid(
                    () -> Ticket.submit(CUSTOMER, subject, BODY, CLOCK), "subject", "must be at most 255 characters");
        }

        @Test
        void acceptsBodyOfMaxLength() {
            String body = "a".repeat(Ticket.MAX_BODY_LENGTH);

            assertThat(Ticket.submit(CUSTOMER, SUBJECT, body, CLOCK).getBody()).isEqualTo(body);
        }

        @Test
        void rejectsBodyOverMaxLength() {
            String body = "a".repeat(Ticket.MAX_BODY_LENGTH + 1);

            assertInvalid(
                    () -> Ticket.submit(CUSTOMER, SUBJECT, body, CLOCK), "body", "must be at most 10000 characters");
        }

        @Test
        void countsAnEmojiAsOneCharacter() {
            // "😀" is two UTF-16 chars but one code point, as PostgreSQL counts it.
            String maxSubject = "😀".repeat(Ticket.MAX_SUBJECT_LENGTH);
            assertThat(maxSubject).hasSize(2 * Ticket.MAX_SUBJECT_LENGTH);

            assertThat(Ticket.submit(CUSTOMER, maxSubject, BODY, CLOCK).getSubject())
                    .isEqualTo(maxSubject);
            assertInvalid(
                    () -> Ticket.submit(CUSTOMER, maxSubject + "😀", BODY, CLOCK),
                    "subject",
                    "must be at most 255 characters");
        }

        @Test
        void checksLengthAfterTrimming() {
            String subject = "a".repeat(Ticket.MAX_SUBJECT_LENGTH);

            assertThat(Ticket.submit(CUSTOMER, "  " + subject + "  ", BODY, CLOCK)
                            .getSubject())
                    .isEqualTo(subject);
        }

        private static void assertInvalid(Runnable submit, String field, String reason) {
            InvalidTicketException exception = catchThrowableOfType(InvalidTicketException.class, submit::run);

            assertThat(exception).isNotNull();
            assertThat(exception.getField()).isEqualTo(field);
            assertThat(exception.getReason()).isEqualTo(reason);
            assertThat(exception).hasMessage("Invalid ticket " + field + ": " + reason);
        }
    }

    @Nested
    class AllowedTransitions {

        @Test
        void startTriageFromNew() {
            Ticket ticket = ticketIn(TicketStatus.NEW);
            ticket.startTriage();
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.TRIAGING);
        }

        @Test
        void completeTriageFromTriaging() {
            Ticket ticket = ticketIn(TicketStatus.TRIAGING);
            ticket.completeTriage();
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PENDING_REVIEW);
        }

        @Test
        void completeTriageFromNeedsManualTriage() {
            Ticket ticket = ticketIn(TicketStatus.NEEDS_MANUAL_TRIAGE);
            ticket.completeTriage();
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PENDING_REVIEW);
        }

        @Test
        void requestManualTriageFromTriaging() {
            Ticket ticket = ticketIn(TicketStatus.TRIAGING);
            ticket.requestManualTriage();
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEEDS_MANUAL_TRIAGE);
        }

        @Test
        void resolveFromPendingReview() {
            Ticket ticket = ticketIn(TicketStatus.PENDING_REVIEW);
            ticket.resolve();
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        }

        @Test
        void escalateFromNeedsManualTriage() {
            Ticket ticket = ticketIn(TicketStatus.NEEDS_MANUAL_TRIAGE);
            ticket.escalate();
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ESCALATED);
        }

        @Test
        void escalateFromPendingReview() {
            Ticket ticket = ticketIn(TicketStatus.PENDING_REVIEW);
            ticket.escalate();
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ESCALATED);
        }
    }

    @Nested
    class IllegalTransitions {

        static Stream<Arguments> illegalCalls() {
            return Stream.of(
                    Arguments.of(TicketStatus.NEW, "resolve", TicketStatus.RESOLVED),
                    Arguments.of(TicketStatus.NEW, "completeTriage", TicketStatus.PENDING_REVIEW),
                    Arguments.of(TicketStatus.TRIAGING, "startTriage", TicketStatus.TRIAGING),
                    Arguments.of(TicketStatus.TRIAGING, "escalate", TicketStatus.ESCALATED),
                    Arguments.of(TicketStatus.NEEDS_MANUAL_TRIAGE, "resolve", TicketStatus.RESOLVED),
                    Arguments.of(TicketStatus.PENDING_REVIEW, "requestManualTriage", TicketStatus.NEEDS_MANUAL_TRIAGE));
        }

        @ParameterizedTest(name = "{1}() on {0}")
        @MethodSource("illegalCalls")
        void throwsAndKeepsStatus(TicketStatus from, String method, TicketStatus to) {
            assertRejected(ticketIn(from), method, from, to);
        }

        @Test
        void messageNamesTicketAndStatuses() {
            Ticket ticket = ticketIn(TicketStatus.NEW);

            assertThatThrownBy(ticket::resolve)
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .isInstanceOf(TicketDomainException.class)
                    .hasMessage("Ticket " + ticket.getId() + ": cannot change status from NEW to RESOLVED");
        }

        @ParameterizedTest(name = "{1}() on {0}")
        @MethodSource("finalStatusCalls")
        void finalStatusesRejectEveryMethod(TicketStatus finalStatus, String method, TicketStatus to) {
            assertRejected(ticketIn(finalStatus), method, finalStatus, to);
        }

        static Stream<Arguments> finalStatusCalls() {
            return Stream.of(TicketStatus.RESOLVED, TicketStatus.ESCALATED)
                    .flatMap(status -> Stream.of(
                            Arguments.of(status, "startTriage", TicketStatus.TRIAGING),
                            Arguments.of(status, "completeTriage", TicketStatus.PENDING_REVIEW),
                            Arguments.of(status, "requestManualTriage", TicketStatus.NEEDS_MANUAL_TRIAGE),
                            Arguments.of(status, "resolve", TicketStatus.RESOLVED),
                            Arguments.of(status, "escalate", TicketStatus.ESCALATED)));
        }

        private static void assertRejected(Ticket ticket, String method, TicketStatus from, TicketStatus to) {
            InvalidStatusTransitionException exception = catchThrowableOfType(
                    InvalidStatusTransitionException.class, () -> call(method).accept(ticket));

            assertThat(exception).isNotNull();
            assertThat(exception.getTicketId()).isEqualTo(ticket.getId());
            assertThat(exception.getFrom()).isEqualTo(from);
            assertThat(exception.getTo()).isEqualTo(to);
            assertThat(ticket.getStatus()).isEqualTo(from);
        }

        private static Consumer<Ticket> call(String method) {
            return switch (method) {
                case "startTriage" -> Ticket::startTriage;
                case "completeTriage" -> Ticket::completeTriage;
                case "requestManualTriage" -> Ticket::requestManualTriage;
                case "resolve" -> Ticket::resolve;
                case "escalate" -> Ticket::escalate;
                default -> throw new IllegalArgumentException(method);
            };
        }
    }

    @Nested
    class Identity {

        @Test
        void equalityIsById() {
            Ticket ticket = newTicket();
            Ticket other = newTicket();

            assertThat(ticket).isEqualTo(ticket).hasSameHashCodeAs(ticket);
            assertThat(ticket).isNotEqualTo(other);
            assertThat(ticket).isNotEqualTo(null);
            assertThat(ticket).isNotEqualTo("not a ticket");
        }

        @Test
        void equalityIgnoresStatus() {
            Ticket ticket = newTicket();
            int hashBefore = ticket.hashCode();

            ticket.startTriage();

            assertThat(ticket.hashCode()).isEqualTo(hashBefore);
        }

        @ParameterizedTest
        @EnumSource(TicketStatus.class)
        void toStringShowsOnlyIdStatusAndCreatedAt(TicketStatus status) {
            Ticket ticket = ticketIn(status);

            assertThat(ticket.toString())
                    .isEqualTo("Ticket[id=" + ticket.getId() + ", status=" + status + ", createdAt=" + NOW + "]")
                    .doesNotContain(SUBJECT)
                    .doesNotContain(BODY);
        }
    }
}
