package dev.triode.tickets.infra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.triode.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Idempotent {@code POST /api/v1/tickets} (TRI-18) over real HTTP to PostgreSQL. Rows are counted with SQL, so the
 * tests check what is stored, not only what the API answers. Every test uses its own customer and key.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotentSubmissionIntegrationTests {

    private static final String TICKET =
            "{\"subject\": \"Printer offline\", \"body\": \"It stopped after the update.\"}";
    private static final String OTHER_TICKET = "{\"subject\": \"Printer offline\", \"body\": \"Now it is on fire.\"}";

    private final HttpClient http =
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    @Test
    void sameKeyTwiceCreatesOneTicketAndReplaysIt() throws Exception {
        UUID customer = UUID.randomUUID();
        String key = newKey();

        HttpResponse<String> first = submit(customer, key, TICKET);
        HttpResponse<String> retry = submit(customer, key, TICKET);

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(first.headers().firstValue("Idempotent-Replayed")).isEmpty();
        assertThat(retry.statusCode()).isEqualTo(200);
        assertThat(retry.headers().firstValue("Idempotent-Replayed")).hasValue("true");
        assertThat(retry.headers().firstValue("Content-Location"))
                .isPresent()
                .isEqualTo(first.headers().firstValue("Location"));
        assertThat(retry.headers().firstValue("Location")).isEmpty();
        assertThat(json.readTree(retry.body())).isEqualTo(json.readTree(first.body()));
        assertThat(rows(customer)).isOne();
    }

    @Test
    void sameKeyWithDifferentBodyIs422AndCreatesNothing() throws Exception {
        UUID customer = UUID.randomUUID();
        String key = newKey();
        submit(customer, key, TICKET);

        HttpResponse<String> reused = submit(customer, key, OTHER_TICKET);

        assertThat(reused.statusCode()).isEqualTo(422);
        assertThat(reused.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(json.readTree(reused.body()).get("detail").asString())
                .isEqualTo("Idempotency-Key was already used for a different request");
        assertThat(reused.body()).doesNotContain(key).doesNotContain("Printer offline");
        assertThat(rows(customer)).isOne();
    }

    @Test
    void twoCustomersWithTheSameKeyGetTwoTickets() throws Exception {
        String key = newKey();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        HttpResponse<String> forAlice = submit(alice, key, TICKET);
        HttpResponse<String> forBob = submit(bob, key, TICKET);

        assertThat(forAlice.statusCode()).isEqualTo(201);
        assertThat(forBob.statusCode()).isEqualTo(201);
        assertThat(id(forAlice)).isNotEqualTo(id(forBob));
        assertThat(jdbc.queryForObject("select count(*) from tickets where idempotency_key = ?", Integer.class, key))
                .isEqualTo(2);
    }

    @Test
    void noKeyTwiceCreatesTwoTickets() throws Exception {
        UUID customer = UUID.randomUUID();

        HttpResponse<String> first = submit(customer, null, TICKET);
        HttpResponse<String> second = submit(customer, null, TICKET);

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(id(first)).isNotEqualTo(id(second));
        assertThat(rows(customer)).isEqualTo(2);
    }

    /**
     * Parallel retries: the threads are released together, so their inserts race. The unique constraint lets exactly
     * one commit; the others wait for it, fail, reload the winner in a new transaction and replay it.
     *
     * <p>This test proves the <em>outcome</em> (one row, one 201, the same id everywhere), not that a race happened in
     * every run: if the scheduler happened to serialise the requests, each later one would find the winner in its
     * first lookup and never reach the constraint. The constraint path itself is proven deterministically elsewhere:
     * {@code JpaTicketRepositoryTests} (a second insert with the same customer and key is translated, by constraint
     * name, into {@code DuplicateIdempotencyKeyException}) and {@code TicketSubmissionServiceTest} (the failed insert's
     * transaction is rolled back, then the winner is reloaded in a new transaction).
     */
    @Test
    void concurrentRetriesCreateExactlyOneTicket() throws Exception {
        int threads = 10;
        UUID customer = UUID.randomUUID();
        String key = newKey();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<HttpResponse<String>> responses = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return submit(customer, key, TICKET);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("all threads ready")
                    .isTrue();
            start.countDown();
            for (Future<HttpResponse<String>> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
        }

        assertThat(rows(customer)).as("rows stored").isOne();
        assertThat(responses).extracting(HttpResponse::statusCode).allMatch(status -> status == 200 || status == 201);
        assertThat(responses)
                .extracting(HttpResponse::statusCode)
                .filteredOn(status -> status == 201)
                .hasSize(1);
        Set<UUID> ids = new HashSet<>();
        for (HttpResponse<String> response : responses) {
            ids.add(id(response));
            if (response.statusCode() == 200) {
                assertThat(response.headers().firstValue("Idempotent-Replayed")).hasValue("true");
            }
        }
        assertThat(ids).as("ticket ids in the responses").hasSize(1);
        assertThat(ids.iterator().next())
                .isEqualTo(jdbc.queryForObject("select id from tickets where customer_id = ?", UUID.class, customer));
    }

    private static String newKey() {
        return "retry-" + UUID.randomUUID();
    }

    private int rows(UUID customer) {
        Integer count =
                jdbc.queryForObject("select count(*) from tickets where customer_id = ?", Integer.class, customer);
        return count == null ? 0 : count;
    }

    private UUID id(HttpResponse<String> response) {
        JsonNode ticket = json.readTree(response.body());
        return UUID.fromString(ticket.get("id").asString());
    }

    private HttpResponse<String> submit(UUID customer, String idempotencyKey, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/tickets"))
                .header("Content-Type", "application/json")
                .header("X-Customer-Id", customer.toString())
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
