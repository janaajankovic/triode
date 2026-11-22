package dev.triode.tickets.domain;

/**
 * A client-chosen key that makes a ticket submission safe to retry: the same customer sending the same key again gets
 * the original ticket instead of a new one. Keys are scoped per customer.
 *
 * <p>Valid keys are 1 to {@value #MAX_LENGTH} printable ASCII characters (0x20 to 0x7E, the range of an HTTP
 * structured-field string). The value is client input: it is never part of an error message, a {@code TicketView}
 * or {@link #toString()}.
 *
 * @param value the key exactly as sent
 */
public record IdempotencyKey(String value) {

    /** Maximum key length in characters (all ASCII, so also bytes). */
    public static final int MAX_LENGTH = 255;

    /** The name clients know the key by (the HTTP header); used as the field name in validation errors. */
    public static final String NAME = "Idempotency-Key";

    static final String INVALID_REASON = "must be 1 to " + MAX_LENGTH + " printable ASCII characters";

    private static final char FIRST_PRINTABLE = 0x20;
    private static final char LAST_PRINTABLE = 0x7E;

    /**
     * @throws InvalidTicketException if the value is null, empty, longer than {@link #MAX_LENGTH} or contains a
     *     character outside printable ASCII
     */
    public IdempotencyKey {
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH || !isPrintableAscii(value)) {
            throw new InvalidTicketException(NAME, INVALID_REASON);
        }
    }

    private static boolean isPrintableAscii(String value) {
        return value.chars().allMatch(c -> c >= FIRST_PRINTABLE && c <= LAST_PRINTABLE);
    }

    /** Leaves out the value: it is client input and must not end up in logs. */
    @Override
    public String toString() {
        return "IdempotencyKey[<redacted>]";
    }
}
