package com.systemturn.turnos.application;

import com.systemturn.turnos.testsupport.EnabledIfDockerOrRequired;
import com.systemturn.turnos.testsupport.PostgresSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** The 10-thread {@code solicitar} race of the H2 suite, resolved by the unique constraint of a real PostgreSQL. */
@EnabledIfDockerOrRequired
class SolicitarConcurrencyPostgresTest extends SolicitarConcurrencyTest {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        PostgresSupport.register(registry);
    }

    @Override
    @Test
    @DisplayName("Concurrent solicitar on PostgreSQL: exactly numbers 1..10, each once")
    void concurrentRequestsGetEachNumberFromOneToTenExactlyOnce() throws Exception {
        super.concurrentRequestsGetEachNumberFromOneToTenExactlyOnce();
    }

    @Test
    @DisplayName("Concurrent solicitar on PostgreSQL: the context really runs on the container")
    void theContextRunsOnThePostgresContainer() {
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL 17.");
    }
}
