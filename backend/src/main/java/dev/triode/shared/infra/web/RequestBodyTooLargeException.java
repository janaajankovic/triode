package dev.triode.shared.infra.web;

import java.io.Serial;

/** The request declared a body larger than the API accepts; mapped to 413 by {@link GlobalExceptionHandler}. */
class RequestBodyTooLargeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    RequestBodyTooLargeException(long contentLength, long maxBytes) {
        super("Request body of " + contentLength + " bytes exceeds the limit of " + maxBytes + " bytes");
    }
}
