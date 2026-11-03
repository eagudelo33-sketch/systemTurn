package com.systemturn.turnos.infrastructure.persistence;

import com.systemturn.turnos.domain.ServicioNoDisponibleException;
import com.systemturn.turnos.domain.Turno;
import com.zaxxer.hikari.HikariDataSource;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "Fast failure": with a PostgreSQL URL on a port nobody listens on, every port call fails with the domain
 * exception within the configured Hikari connection timeout plus 2 seconds. The port is reserved and released at
 * run time, so it is closed without hard-coding a number; nothing sleeps. No profile is active, so the datasource
 * settings are exactly those of {@code application.yml} (connection-timeout bound included).
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.datasource.username=turnos",
        "spring.datasource.password=turnos"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaTurnoRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DatabaseDownFailFastTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 30);

    @DynamicPropertySource
    static void puertoCerrado(DynamicPropertyRegistry registry) throws IOException {
        int puerto;
        try (ServerSocket socket = new ServerSocket(0)) {
            puerto = socket.getLocalPort();
        }
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + puerto + "/turnos");
    }

    @Autowired
    private JpaTurnoRepository repo;

    @Autowired
    private HikariDataSource pool;

    @Test
    void everyPortCallFailsWithServicioNoDisponibleWithinTheConnectionTimeoutPlusTwoSeconds() {
        Duration limite = Duration.ofMillis(pool.getConnectionTimeout() + 2_000);

        assertFailsFast(limite, () -> repo.maxNumero(HOY));
        assertFailsFast(limite, () -> repo.findByFecha(HOY));
        assertFailsFast(limite, () -> repo.guardar(Turno.nuevo(HOY, 1, LocalTime.of(9, 0))));
    }

    private static void assertFailsFast(Duration limite, ThrowableAssert.ThrowingCallable llamada) {
        long inicio = System.nanoTime();
        assertThatThrownBy(llamada).isInstanceOf(ServicioNoDisponibleException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(limite);
    }

    @Test
    void theConnectionTimeoutBoundIsTheConfiguredFiveSeconds() {
        assertThat(pool.getConnectionTimeout()).isEqualTo(5_000L);
    }
}
