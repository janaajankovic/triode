package dev.triode.tickets.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.triode.shared.api.DomainException;
import dev.triode.tickets.api.TicketView;
import dev.triode.tickets.domain.Ticket;
import dev.triode.tickets.domain.TicketId;
import dev.triode.tickets.domain.TicketNotFoundException;
import dev.triode.tickets.domain.TicketRepository;
import dev.triode.tickets.domain.TicketStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketLookupServiceTest {

    private static final Instant CREATED = Instant.parse("2026-11-20T10:00:00Z");
    private static final UUID OWNER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final UUID SOMEONE_ELSE = UUID.fromString("0b8a5c1e-3f4d-4e2a-9c7b-1d2e3f4a5b6c");
    private static final TicketId ID = TicketId.generate();

    private final TicketRepository repository = mock(TicketRepository.class);
    private final TicketLookupService service = new TicketLookupService(repository);

    private static Ticket storedTicket() {
        return Ticket.reconstitute(
                ID,
                OWNER,
                "Cannot log in",
                "The login page shows an error.",
                TicketStatus.TRIAGING,
                CREATED,
                3L,
                null,
                null);
    }

    @Test
    void ownerGetsTheTicket() {
        when(repository.findById(ID)).thenReturn(Optional.of(storedTicket()));

        TicketView view = service.findForCustomer(ID.value(), OWNER);

        assertThat(view)
                .isEqualTo(new TicketView(
                        ID.value(), "Cannot log in", "The login page shows an error.", "TRIAGING", CREATED));
    }

    @Test
    void anotherCustomerGetsNotFound() {
        when(repository.findById(ID)).thenReturn(Optional.of(storedTicket()));

        TicketNotFoundException e = notFound(SOMEONE_ELSE);

        assertThat(e.kind()).isEqualTo(DomainException.Kind.NOT_FOUND);
        assertThat(e.getTicketId()).isEqualTo(ID);
    }

    @Test
    void absentTicketGetsNotFound() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThat(notFound(OWNER).kind()).isEqualTo(DomainException.Kind.NOT_FOUND);
    }

    /** "Not yours" and "does not exist" must be indistinguishable, and never name the owner. */
    @Test
    void notYoursAndAbsentLookTheSame() {
        when(repository.findById(ID)).thenReturn(Optional.of(storedTicket()));
        TicketNotFoundException notYours = notFound(SOMEONE_ELSE);
        when(repository.findById(ID)).thenReturn(Optional.empty());
        TicketNotFoundException absent = notFound(SOMEONE_ELSE);

        assertThat(notYours).hasMessage(absent.getMessage());
        assertThat(notYours.details()).isEqualTo(absent.details()).isEmpty();
        assertThat(notYours.getMessage()).doesNotContain(OWNER.toString());
    }

    private TicketNotFoundException notFound(UUID caller) {
        return catchThrowableOfType(TicketNotFoundException.class, () -> service.findForCustomer(ID.value(), caller));
    }
}
