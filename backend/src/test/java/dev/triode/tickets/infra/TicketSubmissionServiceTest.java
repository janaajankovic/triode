package dev.triode.tickets.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.triode.tickets.api.TicketView;
import dev.triode.tickets.domain.InvalidTicketException;
import dev.triode.tickets.domain.Ticket;
import dev.triode.tickets.domain.TicketRepository;
import dev.triode.tickets.domain.TicketStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TicketSubmissionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final UUID CUSTOMER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    private final TicketRepository repository = mock(TicketRepository.class);
    private final TicketSubmissionService service =
            new TicketSubmissionService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void savesANewTicketAndReturnsTheSavedState() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        TicketView view = service.submit(CUSTOMER, " Cannot log in ", "Error page");

        ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCustomerId()).isEqualTo(CUSTOMER);
        assertThat(saved.getValue().getSubject()).isEqualTo("Cannot log in");
        assertThat(view.id()).isEqualTo(saved.getValue().getId().value());
        assertThat(view.subject()).isEqualTo("Cannot log in");
        assertThat(view.body()).isEqualTo("Error page");
        assertThat(view.status()).isEqualTo(TicketStatus.NEW.name());
        assertThat(view.createdAt()).isEqualTo(NOW);
    }

    @Test
    void invalidInputIsRejectedBeforeSaving() {
        assertThatThrownBy(() -> service.submit(CUSTOMER, "", "Body")).isInstanceOf(InvalidTicketException.class);

        verifyNoInteractions(repository);
    }
}
