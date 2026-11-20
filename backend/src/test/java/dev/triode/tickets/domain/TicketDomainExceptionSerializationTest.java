package dev.triode.tickets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.triode.shared.api.DomainException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Every Throwable is Serializable; domain exceptions must keep their details when serialized. */
class TicketDomainExceptionSerializationTest {

    private static final TicketId ID = TicketId.generate();

    @Test
    void staleTicketExceptionKeepsTheTicketId() throws Exception {
        StaleTicketException copy = roundTrip(new StaleTicketException(ID));

        assertThat(copy.getTicketId()).isEqualTo(ID);
        assertThat(copy).hasMessage("Ticket " + ID + " was changed or deleted by someone else");
    }

    @Test
    void invalidStatusTransitionExceptionKeepsItsDetails() throws Exception {
        InvalidStatusTransitionException copy =
                roundTrip(new InvalidStatusTransitionException(ID, TicketStatus.RESOLVED, TicketStatus.TRIAGING));

        assertThat(copy.getTicketId()).isEqualTo(ID);
        assertThat(copy.getFrom()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(copy.getTo()).isEqualTo(TicketStatus.TRIAGING);
    }

    @Test
    void invalidTicketExceptionKeepsItsDetails() throws Exception {
        InvalidTicketException copy = roundTrip(new InvalidTicketException("subject", "must not be blank"));

        assertThat(copy.getField()).isEqualTo("subject");
        assertThat(copy.getReason()).isEqualTo("must not be blank");
        assertThat(copy.kind()).isEqualTo(DomainException.Kind.INVALID_INPUT);
        assertThat(copy.details()).containsExactly(Map.entry("subject", "must not be blank"));
    }

    @Test
    void staleTicketExceptionIsAConflict() throws Exception {
        assertThat(roundTrip(new StaleTicketException(ID)).kind()).isEqualTo(DomainException.Kind.CONFLICT);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) in.readObject();
        }
    }
}
