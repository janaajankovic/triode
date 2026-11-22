package dev.triode.tickets.infra;

import dev.triode.tickets.domain.DuplicateIdempotencyKeyException;
import dev.triode.tickets.domain.IdempotencyKey;
import dev.triode.tickets.domain.InvalidTicketException;
import dev.triode.tickets.domain.RequestFingerprint;
import dev.triode.tickets.domain.StaleTicketException;
import dev.triode.tickets.domain.Ticket;
import dev.triode.tickets.domain.TicketId;
import dev.triode.tickets.domain.TicketRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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
 *
 * <p>Idempotency: the unique constraint {@value #IDEMPOTENCY_KEY_CONSTRAINT} (V3 migration) on
 * {@code (customer_id, idempotency_key)} decides between concurrent submissions with the same key. A violation of
 * exactly that constraint, matched by name, becomes {@link DuplicateIdempotencyKeyException}.
 */
@Repository
@Transactional
class JpaTicketRepository implements TicketRepository {

    static final String IDEMPOTENCY_KEY_CONSTRAINT = "tickets_customer_idempotency_key_uk";

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
        } catch (DataIntegrityViolationException e) {
            // Only the idempotency constraint means "another submission with this key won"; any other violation
            // (duplicate id, CHECK) is a bug and stays a DataIntegrityViolationException.
            if (violates(e, IDEMPOTENCY_KEY_CONSTRAINT)) {
                throw new DuplicateIdempotencyKeyException(e);
            }
            throw e;
        }
    }

    private static boolean violates(DataIntegrityViolationException e, String constraint) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return constraint.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }

    private static TicketEntity newEntity(Ticket ticket) {
        // Version stays null, so Spring Data persists (INSERT) instead of merging.
        return new TicketEntity(
                ticket.getId().value(),
                ticket.getCustomerId(),
                ticket.getSubject(),
                ticket.getBody(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                ticket.getIdempotencyKey().map(IdempotencyKey::value).orElse(null),
                ticket.getRequestFingerprint().map(RequestFingerprint::value).orElse(null));
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

    @Override
    @Transactional(readOnly = true)
    public Optional<Ticket> findByIdempotencyKey(UUID customerId, IdempotencyKey key) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(key, "key must not be null");
        return jpa.findByCustomerIdAndIdempotencyKey(customerId, key.value()).map(JpaTicketRepository::toDomain);
    }

    /**
     * Invalid stored data is our fault, not the caller's: the domain's {@link InvalidTicketException} (a client
     * error) is wrapped in an {@link IllegalStateException}, which the API reports as a 500. {@code @Repository}
     * exception translation turns it into a {@code DataAccessException} for callers; still a 500.
     */
    private static Ticket toDomain(TicketEntity entity) {
        try {
            return Ticket.reconstitute(
                    new TicketId(entity.getId()),
                    entity.getCustomerId(),
                    entity.getSubject(),
                    entity.getBody(),
                    entity.getStatus(),
                    entity.getCreatedAt(),
                    entity.getVersion(),
                    entity.getIdempotencyKey() == null ? null : new IdempotencyKey(entity.getIdempotencyKey()),
                    entity.getRequestFingerprint() == null
                            ? null
                            : new RequestFingerprint(entity.getRequestFingerprint()));
        } catch (InvalidTicketException e) {
            throw new IllegalStateException("stored ticket " + entity.getId() + " is invalid", e);
        }
    }
}
