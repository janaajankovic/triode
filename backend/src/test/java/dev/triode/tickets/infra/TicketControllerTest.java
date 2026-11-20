package dev.triode.tickets.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.triode.shared.api.CurrentCustomer;
import dev.triode.tickets.api.TicketLookup;
import dev.triode.tickets.api.TicketSubmission;
import dev.triode.tickets.api.TicketView;
import dev.triode.tickets.domain.InvalidStatusTransitionException;
import dev.triode.tickets.domain.Ticket;
import dev.triode.tickets.domain.TicketId;
import dev.triode.tickets.domain.TicketNotFoundException;
import dev.triode.tickets.domain.TicketStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriUtils;

/**
 * HTTP contract of {@code POST /api/v1/tickets} and {@code GET /api/v1/tickets/{id}}: web slice with the real shared
 * error handling (advice, size filter, header-based CurrentCustomer) and mocked {@link TicketSubmission} and
 * {@link TicketLookup}. The submission mock delegates to {@link Ticket#submit}, so validation results come from the
 * domain, as in production.
 */
@WebMvcTest(
        controllers = TicketController.class,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CurrentCustomer.class))
class TicketControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC);
    private static final String CUSTOMER = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    private static final String VALID_BODY = """
            {"subject": "Cannot log in", "body": "The login page shows an error."}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TicketSubmission submission;

    @MockitoBean
    private TicketLookup lookup;

    @BeforeEach
    void submitThroughTheDomain() {
        when(submission.submit(any(), any(), any()))
                .thenAnswer(call -> TicketViews.of(
                        Ticket.submit(call.getArgument(0), call.getArgument(1), call.getArgument(2), CLOCK)));
    }

    private ResultActions postTicket(String json) throws Exception {
        return mvc.perform(post("/api/v1/tickets")
                .header("X-Customer-Id", CUSTOMER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static ResultActions expectProblem(ResultActions result, int status) throws Exception {
        return result.andExpect(status().is(status))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    private static String json(String subject, String body) {
        return "{\"subject\": \"" + subject + "\", \"body\": \"" + body + "\"}";
    }

    @Test
    void submitReturns201WithLocationAndTicket() throws Exception {
        MvcResult result = postTicket(VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.subject").value("Cannot log in"))
                .andExpect(jsonPath("$.body").value("The login page shows an error."))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-20T10:00:00Z"))
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(UUID.fromString(id)).hasToString(id);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/v1/tickets/" + id);
        verify(submission).submit(UUID.fromString(CUSTOMER), "Cannot log in", "The login page shows an error.");
    }

    @Test
    void blankSubjectIs400WithFieldError() throws Exception {
        expectProblem(postTicket(json("   ", "Body")), 400)
                .andExpect(jsonPath("$.detail").value("Invalid ticket subject: must not be blank"))
                .andExpect(jsonPath("$.errors.subject").value("must not be blank"));
    }

    @Test
    void blankBodyIs400WithFieldError() throws Exception {
        expectProblem(postTicket(json("Subject", "")), 400)
                .andExpect(jsonPath("$.errors.body").value("must not be blank"));
    }

    @Test
    void missingSubjectIs400WithFieldError() throws Exception {
        expectProblem(postTicket("{\"body\": \"Body\"}"), 400)
                .andExpect(jsonPath("$.errors.subject").value("is required"));
    }

    @Test
    void nullBodyIs400WithFieldError() throws Exception {
        expectProblem(postTicket("{\"subject\": \"Subject\", \"body\": null}"), 400)
                .andExpect(jsonPath("$.errors.body").value("is required"));
    }

    @Test
    void subjectOverTheDomainLimitIs400() throws Exception {
        String subject = "s".repeat(Ticket.MAX_SUBJECT_LENGTH + 1);

        expectProblem(postTicket(json(subject, "Body")), 400)
                .andExpect(jsonPath("$.errors.subject").value("must be at most 255 characters"));
    }

    @Test
    void bodyOverTheDomainLimitIs400() throws Exception {
        String body = "b".repeat(Ticket.MAX_BODY_LENGTH + 1);

        expectProblem(postTicket(json("Subject", body)), 400)
                .andExpect(jsonPath("$.errors.body").value("must be at most 10000 characters"));
    }

    @Test
    void missingCustomerHeaderIs400() throws Exception {
        expectProblem(
                        mvc.perform(post("/api/v1/tickets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY)),
                        400)
                .andExpect(jsonPath("$.errors['X-Customer-Id']").value("is required"));

        verifyNoInteractions(submission);
    }

    @Test
    void nonUuidCustomerHeaderIs400() throws Exception {
        for (String value : new String[] {"not-a-uuid", "1-1-1-1-1", " "}) {
            expectProblem(
                            mvc.perform(post("/api/v1/tickets")
                                    .header("X-Customer-Id", value)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(VALID_BODY)),
                            400)
                    .andExpect(jsonPath("$.errors['X-Customer-Id']").exists())
                    .andExpect(content().string(not(containsString("not-a-uuid"))));
        }
        verifyNoInteractions(submission);
    }

    @Test
    void malformedJsonIs400WithoutParserInternals() throws Exception {
        expectProblem(postTicket("{\"subject\": \"Cannot log in\", "), 400)
                .andExpect(jsonPath("$.detail").value("Request body is not valid JSON for this endpoint"))
                .andExpect(content().string(not(containsString("jackson"))));
    }

    @Test
    void unknownPropertyIs400() throws Exception {
        String json = "{\"subject\": \"S\", \"body\": \"B\", \"status\": \"RESOLVED\"}";

        expectProblem(postTicket(json), 400)
                .andExpect(jsonPath("$.errors.status").value("unknown property"));

        verifyNoInteractions(submission);
    }

    /** Names that are not plain identifiers (or too long) are client input and are not echoed. */
    @ParameterizedTest
    @ValueSource(strings = {"<img src=x onerror=alert(1)>", "1abc", "sub ject", "status-x", "ä"})
    void oddUnknownPropertyNameIsNotEchoed(String name) throws Exception {
        String json = "{\"subject\": \"S\", \"body\": \"B\", \"" + name + "\": 1}";

        expectProblem(postTicket(json), 400)
                .andExpect(jsonPath("$.detail").value("Request body contains unknown properties"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(content().string(not(containsString(name))));

        verifyNoInteractions(submission);
    }

    @Test
    void longUnknownPropertyNameIsNotEchoed() throws Exception {
        String longest = "a" + "b".repeat(63); // 64 characters: still echoed
        String tooLong = "a" + "b".repeat(5_000);

        expectProblem(postTicket("{\"" + longest + "\": 1}"), 400)
                .andExpect(jsonPath("$.errors." + longest).value("unknown property"));
        expectProblem(postTicket("{\"" + tooLong + "\": 1}"), 400)
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(content().string(not(containsString(tooLong))));
    }

    @Test
    void bodyOver128KbIs413BeforeParsing() throws Exception {
        String huge = json("Subject", "b".repeat(140_000));

        expectProblem(postTicket(huge), 413)
                .andExpect(jsonPath("$.detail").value("Request body must not exceed 131072 bytes"));

        verifyNoInteractions(submission);
    }

    /** Worst case for the byte limit: every code point a non-BMP character sent as an escaped surrogate pair. */
    @Test
    void largestValidTicketWithEscapedEmojiFitsTheLimit() throws Exception {
        String emoji = "\\uD83D\\uDE00"; // 12 bytes on the wire, one code point
        String json = json(emoji.repeat(Ticket.MAX_SUBJECT_LENGTH), emoji.repeat(Ticket.MAX_BODY_LENGTH));
        assertThat(json.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(120_000);

        postTicket(json).andExpect(status().isCreated());
    }

    @Test
    void conflictDomainExceptionIs409() throws Exception {
        TicketId id = TicketId.generate();
        doThrow(new InvalidStatusTransitionException(id, TicketStatus.RESOLVED, TicketStatus.TRIAGING))
                .when(submission)
                .submit(any(), any(), any());

        expectProblem(postTicket(VALID_BODY), 409)
                .andExpect(jsonPath("$.detail")
                        .value("Ticket " + id + ": cannot change status from RESOLVED to TRIAGING"));
    }

    @Test
    void unexpectedExceptionIs500WithoutInternals() throws Exception {
        doThrow(new IllegalStateException("stored ticket 42 is invalid: secret internals"))
                .when(submission)
                .submit(any(), any(), any());

        expectProblem(postTicket(VALID_BODY), 500)
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    // --- GET /api/v1/tickets/{id} ---

    private ResultActions getTicket(Object id) throws Exception {
        return mvc.perform(get("/api/v1/tickets/{id}", id).header("X-Customer-Id", CUSTOMER));
    }

    @Test
    void getOwnTicketReturns200WithAllFieldsAndNoStore() throws Exception {
        TicketView view = TicketViews.of(
                Ticket.submit(UUID.fromString(CUSTOMER), "Cannot log in", "The login page shows an error.", CLOCK));
        when(lookup.findForCustomer(view.id(), UUID.fromString(CUSTOMER))).thenReturn(view);

        getTicket(view.id())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(view.id().toString()))
                .andExpect(jsonPath("$.subject").value("Cannot log in"))
                .andExpect(jsonPath("$.body").value("The login page shows an error."))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-20T10:00:00Z"));

        verify(lookup).findForCustomer(view.id(), UUID.fromString(CUSTOMER));
    }

    @Test
    void getUnknownTicketIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(lookup.findForCustomer(id, UUID.fromString(CUSTOMER)))
                .thenThrow(new TicketNotFoundException(new TicketId(id)));

        expectProblem(getTicket(id), 404).andExpect(jsonPath("$.detail").value("Ticket " + id + " not found"));
    }

    /**
     * The HTTP layer turns every {@link TicketNotFoundException} into the same 404 body apart from the requested id,
     * and never echoes the caller. That "absent" and "not yours" throw the same exception is covered in
     * {@code TicketLookupServiceTest} and {@code TicketApiIntegrationTests}.
     */
    @Test
    void notFoundBodyIsTheSameShapeForAnyId() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(lookup.findForCustomer(any(), any())).thenAnswer(call -> {
            throw new TicketNotFoundException(new TicketId(call.getArgument(0)));
        });

        String firstBody =
                expectProblem(getTicket(first), 404).andReturn().getResponse().getContentAsString();
        String secondBody =
                expectProblem(getTicket(second), 404).andReturn().getResponse().getContentAsString();

        assertThat(firstBody.replace(first.toString(), "{id}"))
                .isEqualTo(secondBody.replace(second.toString(), "{id}"));
        assertThat(firstBody).doesNotContain(CUSTOMER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "12345", "x<script>y", "1-1-1-1-1x"})
    void nonUuidTicketIdIs400WithoutEchoingIt(String id) throws Exception {
        String encoded = UriUtils.encodePathSegment(id, StandardCharsets.UTF_8);

        expectProblem(getTicket(id), 400)
                .andExpect(jsonPath("$.detail").value("Invalid request parameter"))
                .andExpect(jsonPath("$.errors.id").value("must be a UUID"))
                .andExpect(jsonPath("$.instance").value("/api/v1/tickets/%7Bid%7D"))
                .andExpect(content().string(not(containsString(id))))
                .andExpect(content().string(not(containsString(encoded))));

        verifyNoInteractions(lookup);
    }

    /** An id with a "/" never matches the route: Spring's no-route 404, which must not echo the path either. */
    @Test
    void unknownRouteIs404WithoutEchoingThePath() throws Exception {
        String path = "/api/v1/tickets/abc/x<script>probe";

        expectProblem(mvc.perform(get(path).header("X-Customer-Id", CUSTOMER)), 404)
                .andExpect(jsonPath("$.detail").value("No endpoint matches this request"))
                .andExpect(jsonPath("$.instance").value(startsWith("urn:uuid:")))
                .andExpect(content().string(not(containsString("probe"))))
                .andExpect(content().string(not(containsString("abc"))));

        verifyNoInteractions(lookup);
    }

    @Test
    void getWithoutCustomerHeaderIs400() throws Exception {
        expectProblem(mvc.perform(get("/api/v1/tickets/{id}", UUID.randomUUID())), 400)
                .andExpect(jsonPath("$.errors['X-Customer-Id']").value("is required"));

        verifyNoInteractions(lookup);
    }

    @Test
    void getWithNonUuidCustomerHeaderIs400() throws Exception {
        expectProblem(
                        mvc.perform(
                                get("/api/v1/tickets/{id}", UUID.randomUUID()).header("X-Customer-Id", "not-a-uuid")),
                        400)
                .andExpect(jsonPath("$.errors['X-Customer-Id']").value("must be a UUID"))
                .andExpect(content().string(not(containsString("not-a-uuid"))));

        verifyNoInteractions(lookup);
    }

    @Test
    void wrongContentTypeStays415() throws Exception {
        expectProblem(
                mvc.perform(post("/api/v1/tickets")
                        .header("X-Customer-Id", CUSTOMER)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello")),
                415);
    }
}
