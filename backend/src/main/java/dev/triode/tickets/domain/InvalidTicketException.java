package dev.triode.tickets.domain;

/** A ticket field has a value the domain does not accept, for example a blank subject. */
public class InvalidTicketException extends TicketDomainException {

    private final String field;
    private final String reason;

    public InvalidTicketException(String field, String reason) {
        super("Invalid ticket " + field + ": " + reason);
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
