package dev.triode.tickets.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Idempotency key and request fingerprint on the {@link Ticket} aggregate. */
class TicketIdempotencyTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID CUSTOMER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final IdempotencyKey KEY = new IdempotencyKey("retry-key-1");

    private static Ticket keyed(String subject, String body) {
        return Ticket.submit(CUSTOMER, subject, body, KEY, CLOCK);
    }

    @Test
    void keyedSubmitRecordsKeyAndFingerprintOfTheRawInput() {
        Ticket ticket = keyed("  Cannot log in ", "Error page\n");

        assertThat(ticket.getSubject()).isEqualTo("Cannot log in");
        assertThat(ticket.getBody()).isEqualTo("Error page");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEW);
        assertThat(ticket.getIdempotencyKey()).contains(KEY);
        assertThat(ticket.getRequestFingerprint()).contains(RequestFingerprint.of("  Cannot log in ", "Error page\n"));
    }

    @Test
    void submitWithoutKeyHasNeitherKeyNorFingerprint() {
        Ticket ticket = Ticket.submit(CUSTOMER, "Subject", "Body", CLOCK);

        assertThat(ticket.getIdempotencyKey()).isEmpty();
        assertThat(ticket.getRequestFingerprint()).isEmpty();
    }

    @Test
    void keyedSubmitValidatesLikeAnyOther() {
        assertThatThrownBy(() -> keyed("   ", "Body"))
                .isInstanceOf(InvalidTicketException.class)
                .hasMessage("Invalid ticket subject: must not be blank");
        assertThatThrownBy(() -> keyed("Subject", null))
                .isInstanceOf(InvalidTicketException.class)
                .hasMessage("Invalid ticket body: is required");
        assertThatNullPointerException()
                .isThrownBy(() -> Ticket.submit(CUSTOMER, "Subject", "Body", (IdempotencyKey) null, CLOCK))
                .withMessageContaining("idempotencyKey");
    }

    @Test
    void sameKeyAndSameRawContentIsTheSameRequest() {
        assertThat(keyed("Subject", "Body").isSameRequestAs(keyed("Subject", "Body")))
                .isTrue();
    }

    @Test
    void differentContentIsNotTheSameRequest() {
        Ticket original = keyed("Subject", "Body");

        assertThat(original.isSameRequestAs(keyed("Subject", "Other body"))).isFalse();
        assertThat(original.isSameRequestAs(keyed("Other subject", "Body"))).isFalse();
        // Equal after stripping, but not as received.
        assertThat(original.isSameRequestAs(keyed("Subject ", "Body"))).isFalse();
    }

    @Test
    void differentKeyOrCustomerIsNotTheSameRequest() {
        Ticket original = keyed("Subject", "Body");

        assertThat(original.isSameRequestAs(
                        Ticket.submit(CUSTOMER, "Subject", "Body", new IdempotencyKey("retry-key-2"), CLOCK)))
                .isFalse();
        assertThat(original.isSameRequestAs(Ticket.submit(UUID.randomUUID(), "Subject", "Body", KEY, CLOCK)))
                .isFalse();
    }

    @Test
    void ticketsWithoutKeyAreNeverTheSameRequest() {
        Ticket unkeyed = Ticket.submit(CUSTOMER, "Subject", "Body", CLOCK);

        assertThat(unkeyed.isSameRequestAs(Ticket.submit(CUSTOMER, "Subject", "Body", CLOCK)))
                .isFalse();
        assertThat(unkeyed.isSameRequestAs(keyed("Subject", "Body"))).isFalse();
        assertThat(keyed("Subject", "Body").isSameRequestAs(unkeyed)).isFalse();
    }

    @Test
    void reconstituteCarriesKeyAndFingerprint() {
        Ticket submitted = keyed("Subject", "Body");
        RequestFingerprint fingerprint = submitted.getRequestFingerprint().orElseThrow();

        Ticket stored = Ticket.reconstitute(
                submitted.getId(),
                CUSTOMER,
                "Subject",
                "Body",
                TicketStatus.TRIAGING,
                submitted.getCreatedAt(),
                2L,
                KEY,
                fingerprint);

        assertThat(stored.getIdempotencyKey()).contains(KEY);
        assertThat(stored.getRequestFingerprint()).contains(fingerprint);
        assertThat(stored.isSameRequestAs(keyed("Subject", "Body"))).isTrue();
    }

    @Test
    void reconstituteRejectsOnlyOneOfKeyAndFingerprint() {
        TicketId id = TicketId.generate();
        Instant at = Instant.parse("2026-11-22T10:00:00Z");
        RequestFingerprint fingerprint = RequestFingerprint.of("Subject", "Body");

        assertThatThrownBy(
                        () -> Ticket.reconstitute(id, CUSTOMER, "Subject", "Body", TicketStatus.NEW, at, 0L, KEY, null))
                .isInstanceOf(InvalidTicketException.class)
                .hasMessage("Invalid ticket idempotencyKey: must be stored together with requestFingerprint");
        assertThatThrownBy(() -> Ticket.reconstitute(
                        id, CUSTOMER, "Subject", "Body", TicketStatus.NEW, at, 0L, null, fingerprint))
                .isInstanceOf(InvalidTicketException.class);
    }

    @Test
    void statusRulesAreUnchangedForKeyedTickets() {
        Ticket ticket = keyed("Subject", "Body");

        ticket.startTriage();
        ticket.completeTriage();

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PENDING_REVIEW);
        assertThat(ticket.getIdempotencyKey()).contains(KEY);
        assertThatThrownBy(ticket::startTriage).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void toStringLeavesOutTheKey() {
        Ticket ticket = keyed("Subject", "Body");

        assertThat(ticket.toString()).doesNotContain("retry-key-1").doesNotContain("Subject");
    }
}
