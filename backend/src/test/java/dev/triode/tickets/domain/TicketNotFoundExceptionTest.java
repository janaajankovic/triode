package dev.triode.tickets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.triode.shared.api.DomainException;
import org.junit.jupiter.api.Test;

class TicketNotFoundExceptionTest {

    private static final TicketId ID = TicketId.generate();

    @Test
    void namesOnlyTheRequestedTicket() {
        TicketNotFoundException exception = new TicketNotFoundException(ID);

        assertThat(exception)
                .isInstanceOf(TicketDomainException.class)
                .hasMessage("Ticket " + ID + " not found")
                .hasNoCause();
        assertThat(exception.kind()).isEqualTo(DomainException.Kind.NOT_FOUND);
        assertThat(exception.details()).isEmpty();
        assertThat(exception.getTicketId()).isEqualTo(ID);
    }
}
