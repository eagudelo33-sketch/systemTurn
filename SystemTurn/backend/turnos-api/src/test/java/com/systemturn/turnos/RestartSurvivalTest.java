package com.systemturn.turnos;

import com.systemturn.turnos.application.TurnoService;
import com.systemturn.turnos.domain.EstadoTurno;
import com.systemturn.turnos.domain.Turno;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two application starts over the same H2 file: what one run wrote, the next run reads. The URL is passed as a
 * command-line argument because builder default properties would lose against application.yml.
 */
class RestartSurvivalTest {

    @TempDir
    Path directorio;

    private ConfigurableApplicationContext arrancar() {
        return new SpringApplicationBuilder(TurnosApiApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=jdbc:h2:file:" + directorio.resolve("turnos").toAbsolutePath().toString().replace('\\', '/'));
    }

    @Test
    void ticketsAndTheirStateSurviveARestartOnTheSameFile() {
        long id;
        int numero;
        int enFila;
        try (ConfigurableApplicationContext primero = arrancar()) {
            TurnoService service = primero.getBean(TurnoService.class);
            Turno solicitado = service.solicitar();
            id = solicitado.getId();
            numero = solicitado.getNumero();
            service.cancelar(id);
            enFila = service.fila().size();
        }

        try (ConfigurableApplicationContext segundo = arrancar()) {
            TurnoService service = segundo.getBean(TurnoService.class);

            Turno despues = service.dashboard().turnos().stream()
                    .filter(t -> t.getId() == id).findFirst().orElseThrow();
            assertThat(despues.getNumero()).isEqualTo(numero);
            assertThat(despues.getEstado()).isEqualTo(EstadoTurno.CANCELADO);
            assertThat(service.fila()).hasSize(enFila);
            assertThat(service.solicitar().getNumero()).isEqualTo(numero + 1);
        }
    }
}
