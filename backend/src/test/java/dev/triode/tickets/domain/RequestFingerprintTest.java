package dev.triode.tickets.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RequestFingerprintTest {

    @Test
    void isStableForEqualInput() {
        assertThat(RequestFingerprint.of("Cannot log in", "Error page"))
                .isEqualTo(RequestFingerprint.of("Cannot log in", "Error page"));
    }

    /** Pins the encoding: length (4 bytes, big-endian) + UTF-16BE code units, subject then body. */
    @Test
    void matchesTheDocumentedEncoding() {
        // printf '\x00\x00\x00\x01\x00a\x00\x00\x00\x01\x00b' | sha256sum
        assertThat(RequestFingerprint.of("a", "b").value())
                .isEqualTo("738875f5d9549caf11931cbf3c35e1555aded074aebd724c359d0f26d02b237c");
        // Two empty strings: eight zero bytes.
        assertThat(RequestFingerprint.of("", "").value())
                .isEqualTo("af5570f5a1810b7af78caf4bc70a660f0df51e42baf91d4de5b2328de0e83dfc");
    }

    @Test
    void differsForDifferentSubjectOrBody() {
        RequestFingerprint original = RequestFingerprint.of("Subject", "Body");

        assertThat(RequestFingerprint.of("Subject!", "Body")).isNotEqualTo(original);
        assertThat(RequestFingerprint.of("Subject", "Body!")).isNotEqualTo(original);
        assertThat(RequestFingerprint.of("Body", "Subject")).isNotEqualTo(original);
    }

    @Test
    void keepsTheBoundaryBetweenSubjectAndBody() {
        assertThat(RequestFingerprint.of("ab", "c")).isNotEqualTo(RequestFingerprint.of("a", "bc"));
        assertThat(RequestFingerprint.of("abc", "")).isNotEqualTo(RequestFingerprint.of("", "abc"));
    }

    @Test
    void hashesTheRawInputIncludingWhitespace() {
        assertThat(RequestFingerprint.of(" Subject", "Body")).isNotEqualTo(RequestFingerprint.of("Subject", "Body"));
        assertThat(RequestFingerprint.of("Subject", "Body\n")).isNotEqualTo(RequestFingerprint.of("Subject", "Body"));
    }

    /** UTF-8 encoding would turn both lone surrogates into '?' and make these equal. */
    @Test
    void distinguishesDifferentLoneSurrogates() {
        assertThat(RequestFingerprint.of("S", "x\uD800")).isNotEqualTo(RequestFingerprint.of("S", "x\uDC00"));
        assertThat(RequestFingerprint.of("S", "x\uD800")).isNotEqualTo(RequestFingerprint.of("S", "x?"));
    }

    @Test
    void isSixtyFourLowercaseHexCharacters() {
        assertThat(RequestFingerprint.of("Subject", "😀 Body").value()).matches("[0-9a-f]{64}");
    }

    @Test
    void rejectsNullInputAsProgrammingError() {
        assertThatNullPointerException().isThrownBy(() -> RequestFingerprint.of(null, "Body"));
        assertThatNullPointerException().isThrownBy(() -> RequestFingerprint.of("Subject", null));
    }

    @Test
    void acceptsAStoredFingerprint() {
        String stored = RequestFingerprint.of("a", "b").value();

        assertThat(new RequestFingerprint(stored)).isEqualTo(RequestFingerprint.of("a", "b"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "738875F5D9549CAF11931CBF3C35E1555ADED074AEBD724C359D0F26D02B237C", // uppercase
                "738875f5d9549caf11931cbf3c35e1555aded074aebd724c359d0f26d02b237", // 63
                "738875f5d9549caf11931cbf3c35e1555aded074aebd724c359d0f26d02b237c0", // 65
                "g38875f5d9549caf11931cbf3c35e1555aded074aebd724c359d0f26d02b237c"
            })
    void rejectsInvalidStoredValues(String value) {
        assertThatThrownBy(() -> new RequestFingerprint(value))
                .isInstanceOf(InvalidTicketException.class)
                .hasMessage("Invalid ticket requestFingerprint: must be 64 lowercase hex characters");
    }
}
