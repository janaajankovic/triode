package dev.triode.tickets.infra;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link TicketEntity}; used only by {@link JpaTicketRepository}. */
interface TicketJpaRepository extends JpaRepository<TicketEntity, UUID> {

    /** Served by the index of the unique constraint {@code tickets_customer_idempotency_key_uk}. */
    Optional<TicketEntity> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);
}
