package dev.triode.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Serial;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    private static final class TestException extends DomainException {

        @Serial
        private static final long serialVersionUID = 1L;

        TestException(Kind kind, Map<String, String> details) {
            super(kind, "rule broken", details);
        }

        TestException(Kind kind, Throwable cause) {
            super(kind, "rule broken", cause);
        }

        TestException(Kind kind) {
            super(kind, "rule broken");
        }
    }

    @Test
    void exposesKindMessageAndDetailsInInsertionOrder() {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("subject", "must not be blank");
        details.put("body", "is required");

        TestException exception = new TestException(DomainException.Kind.INVALID_INPUT, details);

        assertThat(exception.kind()).isEqualTo(DomainException.Kind.INVALID_INPUT);
        assertThat(exception).hasMessage("rule broken");
        assertThat(exception.details().keySet()).containsExactly("subject", "body");
    }

    @Test
    void copiesDetailsAndKeepsThemUnmodifiable() {
        Map<String, String> details = new HashMap<>(Map.of("subject", "must not be blank"));
        TestException exception = new TestException(DomainException.Kind.INVALID_INPUT, details);

        details.put("body", "is required");

        assertThat(exception.details()).containsOnlyKeys("subject");
        assertThatThrownBy(() -> exception.details().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void detailsAreEmptyWithoutDetails() {
        IllegalStateException cause = new IllegalStateException("db");

        assertThat(new TestException(DomainException.Kind.NOT_FOUND).details()).isEmpty();
        assertThat(new TestException(DomainException.Kind.CONFLICT, cause).details())
                .isEmpty();
        assertThat(new TestException(DomainException.Kind.CONFLICT, cause)).hasCause(cause);
    }

    @Test
    void unprocessableIsAKind() {
        assertThat(new TestException(DomainException.Kind.UNPROCESSABLE).kind())
                .isEqualTo(DomainException.Kind.UNPROCESSABLE);
        assertThat(DomainException.Kind.values())
                .containsExactly(
                        DomainException.Kind.INVALID_INPUT,
                        DomainException.Kind.CONFLICT,
                        DomainException.Kind.NOT_FOUND,
                        DomainException.Kind.UNPROCESSABLE);
    }

    @Test
    void rejectsNullKindAndNullDetailValues() {
        assertThatNullPointerException().isThrownBy(() -> new TestException(null));
        Map<String, String> withNull = new HashMap<>();
        withNull.put("subject", null);
        assertThatNullPointerException()
                .isThrownBy(() -> new TestException(DomainException.Kind.INVALID_INPUT, withNull));
    }
}
