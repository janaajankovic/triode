package dev.triode.tickets.infra;

import dev.triode.tickets.domain.Ticket;
import dev.triode.tickets.domain.TicketId;
import dev.triode.tickets.domain.TicketRepository;
import java.util.Objects;
import java.util.Optional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for the {@link TicketRepository} port.
 *
 * <p>Optimistic locking: an existing ticket is saved only if its version still matches the stored row. The adapter
 * compares versions before changing anything, and Hibernate's {@code UPDATE ... WHERE id = ? AND version = ?} covers
 * a concurrent commit between that check and the flush. Either way a stale ticket raises
 * {@link ObjectOptimisticLockingFailureException} and never overwrites the newer row.
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
        // Flush so the insert or update (and its version check) runs now and the new version is known.
        return toDomain(jpa.saveAndFlush(entity));
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
                .orElseThrow(() -> staleTicket(ticket)); // deleted since it was loaded
        if (!entity.getVersion().equals(ticket.getVersion())) {
            throw staleTicket(ticket);
        }
        // Status is the only field that can change after submit.
        entity.setStatus(ticket.getStatus());
        return entity;
    }

    private static ObjectOptimisticLockingFailureException staleTicket(Ticket ticket) {
        return new ObjectOptimisticLockingFailureException(
                TicketEntity.class, ticket.getId().value());
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
