package dev.triode.tickets.domain;

import java.io.Serial;
import java.util.Map;

/**
 * The customer sent an idempotency key they already used for a submission with a different subject or body. Kind
 * {@code UNPROCESSABLE}: the request is well-formed, but it cannot be a retry of the original. The message and details
 * never contain the key (client input).
 */
public class IdempotencyKeyReusedException extends TicketDomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    static final String REASON = "was already used for a different request";

    public IdempotencyKeyReusedException() {
        super(Kind.UNPROCESSABLE, IdempotencyKey.NAME + " " + REASON, Map.of(IdempotencyKey.NAME, REASON));
    }
}
