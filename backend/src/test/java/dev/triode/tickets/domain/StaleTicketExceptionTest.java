package dev.triode.tickets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StaleTicketExceptionTest {

    private static final TicketId ID = TicketId.generate();

    @Test
    void namesTheTicket() {
        StaleTicketException exception = new StaleTicketException(ID);

        assertThat(exception)
                .isInstanceOf(TicketDomainException.class)
                .hasMessage("Ticket " + ID + " was changed or deleted by someone else")
                .hasNoCause();
        assertThat(exception.getTicketId()).isEqualTo(ID);
    }

    @Test
    void keepsTheCause() {
        IllegalStateException cause = new IllegalStateException("version mismatch");

        assertThat(new StaleTicketException(ID, cause)).hasCause(cause);
    }
}
