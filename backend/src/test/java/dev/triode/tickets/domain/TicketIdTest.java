package dev.triode.tickets.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketIdTest {

    @Test
    void rejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> new TicketId(null)).withMessageContaining("ticket id");
    }

    @Test
    void generateCreatesDistinctIds() {
        assertThat(TicketId.generate()).isNotEqualTo(TicketId.generate());
    }

    @Test
    void toStringIsTheUuid() {
        UUID uuid = UUID.fromString("0b6a3c2e-6f1d-4a8e-9c3b-2d7f5e1a4b90");

        assertThat(new TicketId(uuid)).hasToString("0b6a3c2e-6f1d-4a8e-9c3b-2d7f5e1a4b90");
    }
}
