package dev.triode.tickets.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.triode.TestcontainersConfiguration;
import dev.triode.tickets.domain.InvalidTicketException;
import dev.triode.tickets.domain.Ticket;
import dev.triode.tickets.domain.TicketId;
import dev.triode.tickets.domain.TicketRepository;
import dev.triode.tickets.domain.TicketStatus;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link JpaTicketRepository} against PostgreSQL + pgvector with the Flyway schema. No test-managed transaction:
 * every repository call commits on its own, as in production. Each test uses fresh random ids.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JpaTicketRepositoryTests {

    // Nanoseconds on purpose: submit truncates to micros, so the round-trip must still be exact.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T09:30:15.123456789Z"), ZoneOffset.UTC);

    @Autowired
    private TicketRepository tickets;

    @Autowired
    private JdbcTemplate jdbc;

    private static Ticket newTicket() {
        return Ticket.submit(UUID.randomUUID(), "Cannot log in 😀", "The login page shows an error.", CLOCK);
    }

    @Test
    void saveAndFindByIdRoundTripsEveryField() {
        Ticket submitted = newTicket();

        Ticket saved = tickets.save(submitted);
        Ticket loaded = tickets.findById(submitted.getId()).orElseThrow();

        assertThat(saved.getVersion()).isZero();
        assertThat(submitted.getVersion()).as("argument is left unchanged").isNull();
        assertThat(loaded.getId()).isEqualTo(submitted.getId());
        assertThat(loaded.getCustomerId()).isEqualTo(submitted.getCustomerId());
        assertThat(loaded.getSubject()).isEqualTo(submitted.getSubject());
        assertThat(loaded.getBody()).isEqualTo(submitted.getBody());
        assertThat(loaded.getStatus()).isEqualTo(TicketStatus.NEW);
        assertThat(loaded.getCreatedAt()).isEqualTo(Instant.parse("2026-11-18T09:30:15.123456Z"));
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void statusChangeIsPersistedAndVersionIncreases() {
        Ticket saved = tickets.save(newTicket());

        saved.startTriage();
        Ticket updated = tickets.save(saved);
        Ticket loaded = tickets.findById(saved.getId()).orElseThrow();

        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(loaded.getStatus()).isEqualTo(TicketStatus.TRIAGING);
        assertThat(loaded.getVersion()).isEqualTo(1L);
        assertThat(loaded.getCreatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void findByIdOfUnknownIdIsEmpty() {
        assertThat(tickets.findById(TicketId.generate())).isEmpty();
    }

    @Test
    void lostUpdateIsRejected() {
        TicketId id = tickets.save(newTicket()).getId();
        Ticket first = tickets.findById(id).orElseThrow();
        Ticket second = tickets.findById(id).orElseThrow();

        first.startTriage();
        first.requestManualTriage();
        tickets.save(first);
        second.startTriage();

        assertThatThrownBy(() -> tickets.save(second)).isInstanceOf(OptimisticLockingFailureException.class);
        Ticket loaded = tickets.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(TicketStatus.NEEDS_MANUAL_TRIAGE);
        assertThat(loaded.getVersion()).isEqualTo(1L);
    }

    @Test
    void savingADeletedTicketIsRejected() {
        Ticket saved = tickets.save(newTicket());
        jdbc.update("delete from tickets where id = ?", saved.getId().value());

        saved.startTriage();

        assertThatThrownBy(() -> tickets.save(saved)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void checkConstraintRejectsUnknownStatus() {
        assertThatThrownBy(() -> insertRow("Subject", "BOGUS"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("tickets_status_check");
    }

    @Test
    void findByIdFailsLoudlyOnInvalidStoredData() {
        UUID id = insertRow(" padded subject ", "NEW");

        assertThatThrownBy(() -> tickets.findById(new TicketId(id)))
                .isInstanceOf(InvalidTicketException.class)
                .hasMessage("Invalid ticket subject: must not have leading or trailing whitespace");
    }

    private UUID insertRow(String subject, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into tickets (id, customer_id, subject, body, status, created_at, version)"
                        + " values (?, ?, ?, ?, ?, ?, 0)",
                id,
                UUID.randomUUID(),
                subject,
                "Body",
                status,
                Timestamp.from(CLOCK.instant()));
        return id;
    }
}
