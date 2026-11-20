package dev.triode.shared.infra.web;

import dev.triode.shared.api.CurrentCustomer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * TEMPORARY {@link CurrentCustomer} that trusts the {@code X-Customer-Id} request header. It exists only until
 * authentication arrives in Sprint 4, when it is replaced by an implementation that reads the customer id from the
 * login token, and this class is deleted.
 *
 * <p>The header is not authenticated: any caller can claim any customer id. Nothing may rely on it for access control.
 *
 * <p>The injected {@link HttpServletRequest} is a Spring proxy that resolves to the current request on each call, so
 * this singleton is safe to share between requests.
 */
@Component
class HeaderCurrentCustomer implements CurrentCustomer {

    static final String HEADER = "X-Customer-Id";

    private final HttpServletRequest request;

    HeaderCurrentCustomer(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * @throws InvalidCustomerIdException if the header is missing, blank or not a UUID (reported as 400)
     */
    @Override
    public UUID customerId() {
        String value = request.getHeader(HEADER);
        if (value == null || value.isBlank()) {
            throw new InvalidCustomerIdException("is required");
        }
        String candidate = value.strip();
        UUID id;
        try {
            id = UUID.fromString(candidate);
        } catch (IllegalArgumentException e) {
            throw new InvalidCustomerIdException("must be a UUID");
        }
        // UUID.fromString also accepts short forms such as "1-1-1-1-1"; only the canonical 36-character form is valid.
        if (!id.toString().equalsIgnoreCase(candidate)) {
            throw new InvalidCustomerIdException("must be a UUID");
        }
        return id;
    }
}
