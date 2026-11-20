package dev.triode.tickets.domain;

import java.io.Serial;
import java.util.Map;

/**
 * A ticket field has a value the domain does not accept, for example a blank subject. Kind {@code INVALID_INPUT};
 * details map the field to the reason.
 */
public class InvalidTicketException extends TicketDomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;
    private final String reason;

    public InvalidTicketException(String field, String reason) {
        super(Kind.INVALID_INPUT, "Invalid ticket " + field + ": " + reason, Map.of(field, reason));
        this.field = field;
        this.reason = reason;
    }

    public String getField() {
        return field;
    }

    public String getReason() {
        return reason;
    }
}
