package dev.triode.tickets.infra;

import dev.triode.tickets.domain.StaleTicketException;
import dev.triode.tickets.domain.Ticket;
import dev.triode.tickets.domain.TicketId;
import dev.triode.tickets.domain.TicketRepository;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for the {@link TicketRepository} port.
 *
 * <p>Optimistic locking: an existing ticket is saved only if its version still matches the stored row. The adapter
 * compares versions before changing anything, and Hibernate's {@code UPDATE ... WHERE id = ? AND version = ?} covers
 * a concurrent commit between that check and the flush; Spring's {@link OptimisticLockingFailureException} from that
 * update is translated. Either way a stale ticket raises {@link StaleTicketException} and never overwrites the newer
 * row.
 */
@Repository
@Transactional
class JpaTicketRepository implements TicketRepository {

    private final TicketJpaRepository jpa;

    JpaTicketRepository(TicketJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Ticket save(Ticket ticket) {
        Objects.requireNonNull(ticket, "ticket must not be null");
        TicketEntity entity = ticket.getVersion() == null ? newEntity(ticket) : updatedEntity(ticket);
        try {
            // Flush so the insert or update (and its version check) runs now and the new version is known.
            return toDomain(jpa.saveAndFlush(entity));
        } catch (OptimisticLockingFailureException e) {
            throw new StaleTicketException(ticket.getId(), e);
        }
    }

    private static TicketEntity newEntity(Ticket ticket) {
        // Version stays null, so Spring Data persists (INSERT) instead of merging.
        return new TicketEntity(
                ticket.getId().value(),
                ticket.getCustomerId(),
                ticket.getSubject(),
                ticket.getBody(),
                ticket.getStatus(),
                ticket.getCreatedAt());
    }

    private TicketEntity updatedEntity(Ticket ticket) {
        TicketEntity entity = jpa.findById(ticket.getId().value())
                .orElseThrow(() -> new StaleTicketException(ticket.getId())); // deleted since it was loaded
        if (!entity.getVersion().equals(ticket.getVersion())) {
            throw new StaleTicketException(ticket.getId());
        }
        // Copies every field of Ticket that can change after submit; today that is only status.
        // If Ticket gets another mutable field, copy it here too, or its changes are silently dropped on update.
        entity.setStatus(ticket.getStatus());
        return entity;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Ticket> findById(TicketId id) {
        Objects.requireNonNull(id, "id must not be null");
        return jpa.findById(id.value()).map(JpaTicketRepository::toDomain);
    }

    private static Ticket toDomain(TicketEntity entity) {
        return Ticket.reconstitute(
                new TicketId(entity.getId()),
                entity.getCustomerId(),
                entity.getSubject(),
                entity.getBody(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getVersion());
    }
}
