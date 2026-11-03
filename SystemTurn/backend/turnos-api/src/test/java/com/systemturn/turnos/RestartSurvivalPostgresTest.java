package com.systemturn.turnos;

import com.systemturn.turnos.domain.EstadoTurno;
import com.systemturn.turnos.domain.Turno;
import com.systemturn.turnos.domain.TurnoRepository;
import com.systemturn.turnos.testsupport.EnabledIfDockerOrRequired;
import com.systemturn.turnos.testsupport.PostgresSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Two application starts over the same PostgreSQL container: what the first context wrote, the second reads. */
@EnabledIfDockerOrRequired
class RestartSurvivalPostgresTest extends RestartSurvivalTest {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 29);

    @BeforeEach
    void baseVacia() {
        PostgresSupport.cleanTickets();
    }

    @Override
    String[] argumentos() {
        return Stream.concat(Stream.of("--spring.profiles.active=test"), Stream.of(PostgresSupport.arguments()))
                .toArray(String[]::new);
    }

    @Test
    @DisplayName("Restart survival on PostgreSQL: tickets 1-11 of 2026-10-29 with 1 ATENDIDO come back unchanged")
    void restartSurvivalOnPostgresql() {
        try (ConfigurableApplicationContext primero = arrancar()) {
            TurnoRepository repo = primero.getBean(TurnoRepository.class);
            for (int numero = 1; numero <= 11; numero++) {
                repo.guardar(Turno.nuevo(DIA, numero, LocalTime.of(8, numero, 30)));
            }
            Turno primeroEnLlegar = repo.findByFecha(DIA).stream().filter(t -> t.getNumero() == 1).findFirst().orElseThrow();
            primeroEnLlegar.atender(LocalTime.of(8, 45, 10));
            repo.guardar(primeroEnLlegar);
        }

        try (ConfigurableApplicationContext segundo = arrancar()) {
            List<Turno> turnos = segundo.getBean(TurnoRepository.class).findByFecha(DIA).stream()
                    .sorted((a, b) -> Integer.compare(a.getNumero(), b.getNumero())).toList();

            assertThat(turnos).hasSize(11);
            assertThat(turnos).extracting(Turno::getNumero).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
            assertThat(turnos.get(0).getEstado()).isEqualTo(EstadoTurno.ATENDIDO);
            assertThat(turnos.get(0).getHoraAtencion()).isEqualTo(LocalTime.of(8, 45, 10));
            assertThat(turnos.subList(1, 11)).extracting(Turno::getEstado).containsOnly(EstadoTurno.PENDIENTE);
            assertThat(turnos).extracting(Turno::getHoraSolicitud)
                    .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 11)
                            .mapToObj(n -> LocalTime.of(8, n, 30)).toList());
        }
    }
}
