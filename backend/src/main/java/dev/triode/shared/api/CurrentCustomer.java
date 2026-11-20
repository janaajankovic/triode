package dev.triode.shared.api;

import java.util.UUID;

/**
 * The customer making the current request. Controllers take the caller's identity from here, never from the request
 * body.
 *
 * <p>Until authentication exists (Sprint 4) the only implementation is a TEMPORARY one that reads the unauthenticated
 * {@code X-Customer-Id} header; it will be replaced by one that reads the customer id from the login token.
 */
public interface CurrentCustomer {

    /**
     * The current customer's id.
     *
     * @throws RuntimeException if the request carries no valid customer identity; the global exception handler
     *     reports it as 400 (401 once authentication exists)
     */
    UUID customerId();
}
