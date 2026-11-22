package dev.triode.tickets.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.triode.tickets.api.SubmittedTicket;
import dev.triode.tickets.api.TicketView;
import dev.triode.tickets.domain.DuplicateIdempotencyKeyException;
import dev.triode.tickets.domain.IdempotencyKey;
import dev.triode.tickets.domain.IdempotencyKeyReusedException;
import dev.triode.tickets.domain.InvalidTicketException;
import dev.triode.tickets.domain.Ticket;
import dev.triode.tickets.domain.TicketRepository;
import dev.triode.tickets.domain.TicketStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class TicketSubmissionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID CUSTOMER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final String KEY = "retry-key-1";

    private final TicketRepository repository = mock(TicketRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final TicketSubmissionService service = new TicketSubmissionService(repository, CLOCK, transactions);

    /** What an earlier submission with the same key stored. */
    private static Ticket stored(String subject, String body) {
        return Ticket.submit(CUSTOMER, subject, body, new IdempotencyKey(KEY), CLOCK);
    }

    @Test
    void savesANewTicketAndReturnsTheSavedState() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        SubmittedTicket result = service.submit(CUSTOMER, " Cannot log in ", "Error page", null);

        ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCustomerId()).isEqualTo(CUSTOMER);
        assertThat(saved.getValue().getSubject()).isEqualTo("Cannot log in");
        assertThat(saved.getValue().getIdempotencyKey()).isEmpty();
        assertThat(result.replayed()).isFalse();
        TicketView view = result.ticket();
        assertThat(view.id()).isEqualTo(saved.getValue().getId().value());
        assertThat(view.subject()).isEqualTo("Cannot log in");
        assertThat(view.body()).isEqualTo("Error page");
        assertThat(view.status()).isEqualTo(TicketStatus.NEW.name());
        assertThat(view.createdAt()).isEqualTo(NOW);
        verify(repository, never()).findByIdempotencyKey(any(), any());
    }

    @Test
    void invalidInputIsRejectedBeforeSaving() {
        assertThatThrownBy(() -> service.submit(CUSTOMER, "", "Body", null)).isInstanceOf(InvalidTicketException.class);
        assertThatThrownBy(() -> service.submit(CUSTOMER, "", "Body", KEY)).isInstanceOf(InvalidTicketException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void malformedKeyIsRejectedBeforeAnyStorageAccess() {
        assertThatThrownBy(() -> service.submit(CUSTOMER, "Subject", "Body", ""))
                .isInstanceOf(InvalidTicketException.class)
                .hasMessage("Invalid ticket Idempotency-Key: must be 1 to 255 printable ASCII characters");

        verifyNoInteractions(repository, transactions);
    }

    @Test
    void firstKeyedSubmissionIsSavedWithKeyAndFingerprint() {
        when(repository.findByIdempotencyKey(CUSTOMER, new IdempotencyKey(KEY))).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        SubmittedTicket result = service.submit(CUSTOMER, "Subject", "Body", KEY);

        ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getIdempotencyKey()).contains(new IdempotencyKey(KEY));
        assertThat(saved.getValue().getRequestFingerprint()).isPresent();
        assertThat(result.replayed()).isFalse();
        assertThat(result.ticket().id()).isEqualTo(saved.getValue().getId().value());
    }

    @Test
    void retryWithTheSameContentReplaysTheStoredTicket() {
        Ticket original = stored("Subject", "Body");
        when(repository.findByIdempotencyKey(CUSTOMER, new IdempotencyKey(KEY))).thenReturn(Optional.of(original));

        SubmittedTicket result = service.submit(CUSTOMER, "Subject", "Body", KEY);

        assertThat(result.replayed()).isTrue();
        assertThat(result.ticket()).isEqualTo(TicketViews.of(original));
        verify(repository, never()).save(any());
    }

    @Test
    void keyReusedForDifferentContentIsUnprocessable() {
        when(repository.findByIdempotencyKey(CUSTOMER, new IdempotencyKey(KEY)))
                .thenReturn(Optional.of(stored("Subject", "Body")));

        assertThatThrownBy(() -> service.submit(CUSTOMER, "Subject", "Another body", KEY))
                .isInstanceOf(IdempotencyKeyReusedException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void lostRaceReloadsTheWinnerInANewTransactionAndReplaysIt() {
        Ticket winner = stored("Subject", "Body");
        TransactionStatus lookup = mock(TransactionStatus.class);
        TransactionStatus insert = mock(TransactionStatus.class);
        TransactionStatus reload = mock(TransactionStatus.class);
        when(transactions.getTransaction(any())).thenReturn(lookup, insert, reload);
        when(repository.findByIdempotencyKey(CUSTOMER, new IdempotencyKey(KEY)))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(repository.save(any())).thenThrow(new DuplicateIdempotencyKeyException(new RuntimeException("23505")));

        SubmittedTicket result = service.submit(CUSTOMER, "Subject", "Body", KEY);

        assertThat(result.replayed()).isTrue();
        assertThat(result.ticket().id()).isEqualTo(winner.getId().value());
        InOrder order = inOrder(transactions, repository);
        order.verify(transactions).getTransaction(any());
        order.verify(repository).findByIdempotencyKey(any(), any());
        order.verify(transactions).commit(lookup);
        order.verify(transactions).getTransaction(any());
        order.verify(repository).save(any());
        order.verify(transactions).rollback(insert);
        order.verify(transactions).getTransaction(any());
        order.verify(repository).findByIdempotencyKey(any(), any());
        order.verify(transactions).commit(reload);
    }

    @Test
    void lostRaceAgainstDifferentContentIsUnprocessable() {
        when(repository.findByIdempotencyKey(CUSTOMER, new IdempotencyKey(KEY)))
                .thenReturn(Optional.empty(), Optional.of(stored("Subject", "Other body")));
        when(repository.save(any())).thenThrow(new DuplicateIdempotencyKeyException(new RuntimeException("23505")));

        assertThatThrownBy(() -> service.submit(CUSTOMER, "Subject", "Body", KEY))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void missingWinnerAfterALostRaceIsAServerError() {
        when(repository.findByIdempotencyKey(CUSTOMER, new IdempotencyKey(KEY))).thenReturn(Optional.empty());
        DuplicateIdempotencyKeyException duplicate = new DuplicateIdempotencyKeyException(new RuntimeException());
        when(repository.save(any())).thenThrow(duplicate);

        assertThatThrownBy(() -> service.submit(CUSTOMER, "Subject", "Body", KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(duplicate);
        verify(repository, times(2)).findByIdempotencyKey(any(), any());
    }

    @Test
    void everyStepRunsInANewTransaction() {
        when(repository.findByIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.submit(CUSTOMER, "Subject", "Body", KEY);

        ArgumentCaptor<TransactionDefinition> definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactions, times(2)).getTransaction(definitions.capture());
        assertThat(definitions.getAllValues())
                .extracting(TransactionDefinition::getPropagationBehavior)
                .containsOnly(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThat(definitions.getAllValues())
                .extracting(TransactionDefinition::isReadOnly)
                .containsExactly(true, false);
    }
}
