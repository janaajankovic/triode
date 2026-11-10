package dev.triode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Starts the whole application against a real PostgreSQL + pgvector container and checks that
 * Flyway has run the migrations.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TriodeApplicationTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void contextLoads() {}

    @Test
    void flywayEnablesPgvectorExtension() {
        String version =
                jdbc.queryForObject("select extversion from pg_extension where extname = 'vector'", String.class);
        assertThat(version).isNotBlank();
    }

    @Test
    void flywayRecordsInitialMigration() {
        Boolean applied =
                jdbc.queryForObject("select success from flyway_schema_history where version = '1'", Boolean.class);
        assertThat(applied).isTrue();
    }
}
