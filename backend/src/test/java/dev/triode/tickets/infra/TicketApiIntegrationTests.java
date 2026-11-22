package dev.triode.tickets.infra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.triode.TestcontainersConfiguration;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ticket API over real HTTP (embedded Tomcat on a random port) to PostgreSQL: controller, shared error handling,
 * services, repository and Flyway schema. Uses the JDK HTTP client so a body can be sent chunked, without a
 * Content-Length.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TicketApiIntegrationTests {

    private final HttpClient http =
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    @Test
    void submittedTicketIsStoredWithStatusNewAndTheHeadersCustomer() throws Exception {
        UUID customer = UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/tickets"))
                .header("Content-Type", "application/json")
                .header("X-Customer-Id", customer.toString())
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"subject\": \"Cannot log in\", \"body\": \"The login page shows an error.\"}"))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode ticket = json.readTree(response.body());
        UUID id = UUID.fromString(ticket.get("id").asString());
        assertThat(response.headers().firstValue("Location")).hasValue("/api/v1/tickets/" + id);
        assertThat(ticket.get("status").asString()).isEqualTo("NEW");

        Map<String, Object> row = jdbc.queryForMap("select customer_id, status, subject from tickets where id = ?", id);
        assertThat(row)
                .containsEntry("customer_id", customer)
                .containsEntry("status", "NEW")
                .containsEntry("subject", "Cannot log in");
    }

    @Test
    void followingTheLocationHeaderReturnsTheSameTicketToItsCustomer() throws Exception {
        UUID customer = UUID.randomUUID();
        HttpResponse<String> created = submit(customer);
        assertThat(created.statusCode()).isEqualTo(201);
        String location = created.headers().firstValue("Location").orElseThrow();

        HttpResponse<String> fetched = get(location, customer);

        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(json.readTree(fetched.body())).isEqualTo(json.readTree(created.body()));
        assertThat(json.readTree(fetched.body()).get("subject").asString()).isEqualTo("Printer offline");
    }

    @Test
    void anotherCustomerGets404LikeAnUnknownId() throws Exception {
        HttpResponse<String> created = submit(UUID.randomUUID());
        String location = created.headers().firstValue("Location").orElseThrow();
        UUID stranger = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();

        HttpResponse<String> othersTicket = get(location, stranger);
        HttpResponse<String> unknownTicket = get("/api/v1/tickets/" + unknownId, stranger);

        assertThat(othersTicket.statusCode()).isEqualTo(404);
        assertThat(othersTicket.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        String ticketId = location.substring(location.lastIndexOf('/') + 1);
        assertThat(othersTicket.body().replace(ticketId, "{id}"))
                .isEqualTo(unknownTicket.body().replace(unknownId.toString(), "{id}"));
        assertThat(othersTicket.body()).doesNotContain("Printer offline");
    }

    /** No Content-Length, so the size filter cannot see the size; Jackson's document-length limit stops it. */
    @Test
    void chunkedBodyOver128KbIs413() throws Exception {
        byte[] huge =
                ("{\"subject\": \"S\", \"body\": \"" + "b".repeat(140_000) + "\"}").getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/tickets"))
                .header("Content-Type", "application/json")
                .header("X-Customer-Id", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(huge)))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(json.readTree(response.body()).get("detail").asString())
                .isEqualTo("Request body must not exceed 131072 bytes");
    }

    @Test
    void openApiDescribesTheEndpointWithDomainLimits() throws Exception {
        HttpResponse<String> response =
                http.send(HttpRequest.newBuilder(uri("/v3/api-docs")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode spec = json.readTree(response.body());

        JsonNode post = spec.path("paths").path("/api/v1/tickets").path("post");
        assertThat(post.isMissingNode()).as("POST /api/v1/tickets in the spec").isFalse();
        assertThat(post.path("parameters").toString())
                .contains("\"name\":\"X-Customer-Id\"")
                .contains("\"in\":\"header\"");
        JsonNode idempotencyKey = null;
        for (JsonNode parameter : post.path("parameters")) {
            if ("Idempotency-Key".equals(parameter.path("name").asString())) {
                idempotencyKey = parameter;
            }
        }
        assertThat(idempotencyKey).as("Idempotency-Key parameter").isNotNull();
        assertThat(idempotencyKey.path("in").asString()).isEqualTo("header");
        assertThat(idempotencyKey.path("required").asBoolean(false)).isFalse();
        assertThat(idempotencyKey.path("schema").path("maxLength").asInt()).isEqualTo(255);
        for (String status : new String[] {"200", "201", "400", "422"}) {
            assertThat(post.path("responses").path(status).isMissingNode())
                    .as("POST response " + status)
                    .isFalse();
        }
        assertThat(post.path("responses").path("200").path("headers").has("Idempotent-Replayed"))
                .isTrue();
        assertThat(post.path("responses").path("200").path("content").has("application/json"))
                .as("replay returns the TicketView")
                .isTrue();
        assertThat(post.path("responses").path("201").path("content").has("application/json"))
                .isTrue();
        assertThat(post.path("responses").path("422").path("content").has("application/problem+json"))
                .isTrue();

        JsonNode request = spec.path("components").path("schemas").path("SubmitTicketRequest");
        assertThat(request.path("properties").path("subject").path("maxLength").asInt())
                .isEqualTo(255);
        assertThat(request.path("properties").path("body").path("maxLength").asInt())
                .isEqualTo(10_000);
        assertThat(request.path("required").toString()).contains("subject").contains("body");

        JsonNode get = spec.path("paths").path("/api/v1/tickets/{id}").path("get");
        assertThat(get.isMissingNode())
                .as("GET /api/v1/tickets/{id} in the spec")
                .isFalse();
        assertThat(get.path("parameters").toString())
                .contains("\"name\":\"X-Customer-Id\"")
                .contains("\"name\":\"id\"");
        for (String status : new String[] {"200", "400", "404"}) {
            assertThat(get.path("responses").path(status).isMissingNode())
                    .as("GET response " + status)
                    .isFalse();
        }
        assertThat(get.path("responses").path("200").path("content").has("application/json"))
                .as("200 keeps the TicketView body")
                .isTrue();
        assertThat(get.path("responses").path("404").path("content").has("application/problem+json"))
                .isTrue();
        assertThat(get.path("responses").path("400").path("content").has("application/problem+json"))
                .isTrue();
    }

    /** Real Tomcat path handling: an unmatched path gets a generic 404 that does not echo it. */
    @Test
    void unknownRouteIs404WithoutEchoingThePath() throws Exception {
        HttpResponse<String> response = get("/api/v1/tickets/abc/%3Cscript%3Eprobe", UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.get("detail").asString()).isEqualTo("No endpoint matches this request");
        assertThat(problem.get("instance").asString()).startsWith("urn:uuid:");
        assertThat(response.body())
                .doesNotContain("probe")
                .doesNotContain("script")
                .doesNotContain("abc");
    }

    private HttpResponse<String> submit(UUID customer) throws Exception {
        return http.send(
                HttpRequest.newBuilder(uri("/api/v1/tickets"))
                        .header("Content-Type", "application/json")
                        .header("X-Customer-Id", customer.toString())
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"subject\": \"Printer offline\", \"body\": \"It stopped after the update.\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, UUID customer) throws Exception {
        return http.send(
                HttpRequest.newBuilder(uri(path))
                        .header("X-Customer-Id", customer.toString())
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
