package dev.triode.tickets.infra;

import dev.triode.shared.api.CurrentCustomer;
import dev.triode.tickets.api.SubmittedTicket;
import dev.triode.tickets.api.TicketLookup;
import dev.triode.tickets.api.TicketSubmission;
import dev.triode.tickets.api.TicketView;
import dev.triode.tickets.domain.IdempotencyKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for customers' tickets. Errors are problem details from the shared global exception handler.
 *
 * <p>Responses carry subject and body (personal data), so they are sent with {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping(TicketController.BASE_PATH)
class TicketController {

    static final String BASE_PATH = "/api/v1/tickets";

    /** Response header marking a replayed submission (IETF idempotency-key draft and common practice). */
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final TicketSubmission submission;
    private final TicketLookup lookup;
    private final CurrentCustomer currentCustomer;

    TicketController(TicketSubmission submission, TicketLookup lookup, CurrentCustomer currentCustomer) {
        this.submission = submission;
        this.lookup = lookup;
        this.currentCustomer = currentCustomer;
    }

    @Operation(
            summary = "Submit a ticket",
            description = "Send an Idempotency-Key to make retries safe: a retry with the same key and the same subject"
                    + " and body returns the original ticket (200) instead of creating another one.")
    @Parameter(
            name = "X-Customer-Id",
            in = ParameterIn.HEADER,
            required = true,
            description = "Temporary caller identity until authentication (Sprint 4)",
            schema = @Schema(type = "string", format = "uuid"))
    @ApiResponse(
            responseCode = "201",
            description = "Ticket created",
            headers = @Header(name = HttpHeaders.LOCATION, description = "Path of the new ticket"))
    @ApiResponse(
            responseCode = "200",
            description = "Retry with a known Idempotency-Key and the same content: nothing was created; the body is"
                    + " the original ticket in its current state",
            headers = {
                @Header(
                        name = IDEMPOTENT_REPLAYED,
                        description = "Always true on a replay",
                        schema = @Schema(type = "string", allowableValues = "true")),
                @Header(name = HttpHeaders.LOCATION, description = "Path of the original ticket")
            })
    @ApiResponse(
            responseCode = "400",
            description = "Invalid subject, body or Idempotency-Key, malformed JSON, or missing or non-UUID"
                    + " X-Customer-Id",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "422",
            description = "The Idempotency-Key was already used for a different subject or body",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TicketView> submit(
            @RequestBody SubmitTicketRequest request,
            @Parameter(
                            description = "Optional client-chosen key, unique per customer (e.g. a UUID). Reusing it"
                                    + " with the same subject and body (exactly as sent) replays the original ticket;"
                                    + " with a different subject or body it is rejected with 422. Keys do not expire.",
                            schema =
                                    @Schema(
                                            type = "string",
                                            minLength = 1,
                                            maxLength = IdempotencyKey.MAX_LENGTH,
                                            pattern = "^[\\x20-\\x7E]+$",
                                            example = "0d8f2c5e-3b1a-4f7e-9c2d-5a6b7c8d9e0f"))
                    @RequestHeader(name = IdempotencyKey.NAME, required = false)
                    String idempotencyKey) {
        SubmittedTicket result =
                submission.submit(currentCustomer.customerId(), request.subject(), request.body(), idempotencyKey);
        TicketView ticket = result.ticket();
        // Relative URI: correct behind a proxy or load balancer without trusting forwarded headers.
        URI location = URI.create(BASE_PATH + "/" + ticket.id());
        if (result.replayed()) {
            return ResponseEntity.ok()
                    .location(location)
                    .header(IDEMPOTENT_REPLAYED, "true")
                    .cacheControl(CacheControl.noStore())
                    .body(ticket);
        }
        return ResponseEntity.created(location)
                .cacheControl(CacheControl.noStore())
                .body(ticket);
    }

    @Operation(
            summary = "Get one of your tickets",
            description = "An unknown id and another customer's ticket both return the same 404.")
    @Parameter(
            name = "X-Customer-Id",
            in = ParameterIn.HEADER,
            required = true,
            description = "Temporary caller identity until authentication (Sprint 4)",
            schema = @Schema(type = "string", format = "uuid"))
    @ApiResponse(responseCode = "200", description = "The caller's ticket")
    @ApiResponse(
            responseCode = "400",
            description = "The id is not a UUID, or X-Customer-Id is missing or not a UUID",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "No ticket with this id belongs to the caller (unknown and someone else's look the same)",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TicketView> get(@PathVariable UUID id) {
        TicketView ticket = lookup.findForCustomer(id, currentCustomer.customerId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ticket);
    }
}
