package dev.triode.tickets.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.triode.TestcontainersConfiguration;
import dev.triode.shared.api.DomainException;
import dev.triode.tickets.domain.InvalidTicketException;
import dev.triode.tickets.domain.StaleTicketException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    private TicketJpaRepository jpa;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

        assertThatThrownBy(() -> tickets.save(second))
                .isInstanceOf(StaleTicketException.class)
                .hasMessage("Ticket " + id + " was changed or deleted by someone else");
        Ticket loaded = tickets.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(TicketStatus.NEEDS_MANUAL_TRIAGE);
        assertThat(loaded.getVersion()).isEqualTo(1L);
    }

    /**
     * The race the version pre-check cannot see: another transaction commits after the adapter has read the row.
     * The entity with version 0 is already in the persistence context, so the pre-check passes; only Hibernate's
     * {@code UPDATE ... WHERE version = 0} notices, and the adapter translates that failure.
     */
    @Test
    void concurrentCommitAfterPreCheckIsRejected() {
        Ticket saved = tickets.save(newTicket());
        saved.startTriage();
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate separate = new TransactionTemplate(transactionManager);
        separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
                    // Puts the version-0 entity in this transaction's persistence context.
                    assertThat(jpa.findById(saved.getId().value()).orElseThrow().getVersion())
                            .isZero();
                    // Another writer commits version 1 in its own transaction.
                    separate.executeWithoutResult(inner -> jdbc.update(
                            "update tickets set version = 1 where id = ?",
                            saved.getId().value()));
                    tickets.save(saved);
                }))
                .isInstanceOf(StaleTicketException.class)
                .hasCauseInstanceOf(OptimisticLockingFailureException.class);

        Ticket loaded = tickets.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getVersion()).isEqualTo(1L);
        assertThat(loaded.getStatus()).isEqualTo(TicketStatus.NEW);
    }

    @Test
    void savingADeletedTicketIsRejected() {
        Ticket saved = tickets.save(newTicket());
        jdbc.update("delete from tickets where id = ?", saved.getId().value());

        saved.startTriage();

        assertThatThrownBy(() -> tickets.save(saved)).isInstanceOf(StaleTicketException.class);
    }

    @Test
    void savingTheSameNewTicketTwiceFails() {
        Ticket submitted = newTicket();
        tickets.save(submitted);

        // The argument keeps version null; callers must continue with the returned ticket instead.
        assertThatThrownBy(() -> tickets.save(submitted)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(tickets.findById(submitted.getId()).orElseThrow().getVersion())
                .isZero();
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

        // A server-side fault (500), not the caller's invalid input (400). The adapter throws IllegalStateException;
        // @Repository exception translation wraps it in a DataAccessException on the way out.
        assertThatThrownBy(() -> tickets.findById(new TicketId(id)))
                .isNotInstanceOf(DomainException.class)
                .hasMessage("stored ticket " + id + " is invalid")
                .hasCauseInstanceOf(IllegalStateException.class)
                .rootCause()
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
