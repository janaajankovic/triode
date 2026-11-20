package dev.triode.shared.infra.web;

import java.io.Serial;

/** The {@code X-Customer-Id} header is missing or not a UUID; mapped to 400 by {@link GlobalExceptionHandler}. */
class InvalidCustomerIdException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String reason;

    InvalidCustomerIdException(String reason) {
        // The raw header value is never put in the message: it is caller input.
        super(HeaderCurrentCustomer.HEADER + " " + reason);
        this.reason = reason;
    }

    String getReason() {
        return reason;
    }
}
