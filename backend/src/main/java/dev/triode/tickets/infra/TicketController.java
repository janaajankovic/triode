package dev.triode.tickets.infra;

import dev.triode.shared.api.CurrentCustomer;
import dev.triode.tickets.api.TicketLookup;
import dev.triode.tickets.api.TicketSubmission;
import dev.triode.tickets.api.TicketView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    private final TicketSubmission submission;
    private final TicketLookup lookup;
    private final CurrentCustomer currentCustomer;

    TicketController(TicketSubmission submission, TicketLookup lookup, CurrentCustomer currentCustomer) {
        this.submission = submission;
        this.lookup = lookup;
        this.currentCustomer = currentCustomer;
    }

    @Operation(summary = "Submit a ticket")
    @Parameter(
            name = "X-Customer-Id",
            in = ParameterIn.HEADER,
            required = true,
            description = "Temporary caller identity until authentication (Sprint 4)",
            schema = @Schema(type = "string", format = "uuid"))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TicketView> submit(@RequestBody SubmitTicketRequest request) {
        TicketView ticket = submission.submit(currentCustomer.customerId(), request.subject(), request.body());
        // Relative URI: correct behind a proxy or load balancer without trusting forwarded headers.
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + ticket.id()))
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
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TicketView> get(@PathVariable UUID id) {
        TicketView ticket = lookup.findForCustomer(id, currentCustomer.customerId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ticket);
    }
}
