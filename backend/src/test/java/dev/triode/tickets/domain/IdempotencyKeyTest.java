package dev.triode.tickets.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.triode.shared.api.DomainException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class IdempotencyKeyTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "k",
                "0d8f2c5e-3b1a-4f7e-9c2d-5a6b7c8d9e0f",
                " ",
                "~",
                "with space",
                "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}"
            })
    void acceptsPrintableAscii(String value) {
        assertThat(new IdempotencyKey(value).value()).isEqualTo(value);
    }

    @Test
    void acceptsMaxLength() {
        String value = "k".repeat(IdempotencyKey.MAX_LENGTH);

        assertThat(new IdempotencyKey(value).value()).hasSize(255);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "", // empty
                "key\u0001", // control character
                "key\u001F", // last control character below space
                "key\u007F", // DEL
                "key\n",
                "k\tey",
                "schlüssel", // non-ASCII
                "key😀" // non-BMP
            })
    void rejectsInvalidKeysWithoutEchoingThem(String value) {
        assertRejected(value);
    }

    @Test
    void rejectsKeyOverMaxLength() {
        assertRejected("k".repeat(IdempotencyKey.MAX_LENGTH + 1));
    }

    @Test
    void toStringHidesTheValue() {
        assertThat(new IdempotencyKey("secret-key-123").toString())
                .isEqualTo("IdempotencyKey[<redacted>]")
                .doesNotContain("secret-key-123");
    }

    @Test
    void equalityIsByValue() {
        assertThat(new IdempotencyKey("abc"))
                .isEqualTo(new IdempotencyKey("abc"))
                .hasSameHashCodeAs(new IdempotencyKey("abc"))
                .isNotEqualTo(new IdempotencyKey("ABC"));
    }

    private static void assertRejected(String value) {
        InvalidTicketException exception =
                catchThrowableOfType(InvalidTicketException.class, () -> new IdempotencyKey(value));

        assertThat(exception).isNotNull();
        assertThat(exception.kind()).isEqualTo(DomainException.Kind.INVALID_INPUT);
        assertThat(exception.details())
                .containsExactly(Map.entry("Idempotency-Key", "must be 1 to 255 printable ASCII characters"));
        assertThat(exception).hasMessage("Invalid ticket Idempotency-Key: must be 1 to 255 printable ASCII characters");
        if (value != null && !value.isEmpty()) {
            assertThat(exception.getMessage()).doesNotContain(value);
        }
    }
}
