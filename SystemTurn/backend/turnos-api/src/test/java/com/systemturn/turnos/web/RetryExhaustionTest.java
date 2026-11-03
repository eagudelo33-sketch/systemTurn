package com.systemturn.turnos.web;

import com.systemturn.turnos.application.TurnoService;
import com.systemturn.turnos.domain.NumeroDuplicadoException;
import com.systemturn.turnos.domain.Turno;
import com.systemturn.turnos.testsupport.InMemoryTurnoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec scenario "Retry exhaustion unchanged" (concurrency-conflicts): when every retry hits a duplicate number,
 * the HTTP response stays 409 {@code CONFLICTO}. Real service, real controller and real exception handler; only the
 * repository is a fake that always collides.
 */
class RetryExhaustionTest {

    private static final int MAX_INTENTOS = 10;
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-30T14:00:00Z"), ZoneId.of("America/Bogota"));

    /** Every insert collides on the ticket number, as if another request always won the race. */
    private static final class DuplicadoSiempre extends InMemoryTurnoRepository {
        int intentos;

        @Override
        public synchronized Turno guardar(Turno turno) {
            intentos++;
            throw new NumeroDuplicadoException();
        }
    }

    private static MockMvc mvc(TurnoService service) {
        return MockMvcBuilders.standaloneSetup(new TurnoController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Retry exhaustion unchanged: every retry hits a duplicate, solicitar answers 409 CONFLICTO")
    void retryExhaustionUnchangedAnswers409Conflicto() throws Exception {
        DuplicadoSiempre repositorio = new DuplicadoSiempre();

        mvc(new TurnoService(repositorio, RELOJ, MAX_INTENTOS))
                .perform(post("/api/v1/turnos"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICTO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());
        assertThat(repositorio.intentos).isEqualTo(MAX_INTENTOS);
    }

    @Test
    @DisplayName("Retry exhaustion unchanged: the attempt budget is the service's, not a fixed number")
    void retryExhaustionHonoursTheConfiguredAttemptBudget() throws Exception {
        DuplicadoSiempre repositorio = new DuplicadoSiempre();

        mvc(new TurnoService(repositorio, RELOJ, 3))
                .perform(post("/api/v1/turnos"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICTO"));
        assertThat(repositorio.intentos).isEqualTo(3);
    }
}
