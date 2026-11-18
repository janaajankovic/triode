package dev.triode.tickets.infra;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link TicketEntity}; used only by {@link JpaTicketRepository}. */
interface TicketJpaRepository extends JpaRepository<TicketEntity, UUID> {}
