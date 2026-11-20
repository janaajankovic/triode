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
 * Full path over real HTTP (embedded Tomcat on a random port) to PostgreSQL: controller, shared error handling,
 * service, repository and Flyway schema. Uses the JDK HTTP client so a body can be sent chunked, without a
 * Content-Length.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TicketSubmissionIntegrationTests {

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

        JsonNode request = spec.path("components").path("schemas").path("SubmitTicketRequest");
        assertThat(request.path("properties").path("subject").path("maxLength").asInt())
                .isEqualTo(255);
        assertThat(request.path("properties").path("body").path("maxLength").asInt())
                .isEqualTo(10_000);
        assertThat(request.path("required").toString()).contains("subject").contains("body");
    }
}
