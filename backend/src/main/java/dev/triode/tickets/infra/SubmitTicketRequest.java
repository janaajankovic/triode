package dev.triode.tickets.infra;

import dev.triode.tickets.domain.Ticket;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Body of {@code POST /api/v1/tickets}. No Bean Validation on purpose: {@link Ticket#submit} is the one source of
 * truth for the rules. The {@code @Schema} limits only document them in OpenAPI, taken from the domain constants.
 * The caller's identity never comes from the body.
 */
record SubmitTicketRequest(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = Ticket.MAX_SUBJECT_LENGTH,
                description = "Short summary; surrounding whitespace is removed, must not be blank",
                example = "Cannot log in")
        String subject,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = Ticket.MAX_BODY_LENGTH,
                description = "Description of the problem; surrounding whitespace is removed, must not be blank",
                example = "The login page shows an error after I enter my password.")
        String body) {}
