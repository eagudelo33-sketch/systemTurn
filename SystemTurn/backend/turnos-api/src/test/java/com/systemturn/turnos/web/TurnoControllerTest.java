package com.systemturn.turnos.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TurnoControllerTest {

    private static final String BASE = "/api/v1/turnos";

    @Autowired
    private MockMvc mvc;

    @Test
    void dashboardMatchesContract() throws Exception {
        mvc.perform(get(BASE + "/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricas.pendientes").value(5))
                .andExpect(jsonPath("$.metricas.atendidos").value(5))
                .andExpect(jsonPath("$.metricas.cancelados").value(1))
                .andExpect(jsonPath("$.metricas.esperaPromedioMinutos").value(12.2))
                .andExpect(jsonPath("$.siguienteTurno.id").value(6))
                .andExpect(jsonPath("$.siguienteTurno.codigo").value("T-006"))
                .andExpect(jsonPath("$.siguienteTurno.horaSolicitud").value("08:25 a. m."))
                .andExpect(jsonPath("$.siguienteTurno.estado").value("Pendiente"))
                .andExpect(jsonPath("$.turnos.length()").value(11))
                .andExpect(jsonPath("$.turnos[0].horaAtencion").value("08:05 a. m."))
                .andExpect(jsonPath("$.turnos[0].estado").value("Atendido"))
                .andExpect(jsonPath("$.turnos[6].id").value(7))
                .andExpect(jsonPath("$.turnos[6].codigo").value("T-007"))
                .andExpect(jsonPath("$.turnos[6].estado").value("Cancelado"))
                .andExpect(jsonPath("$.turnos[6].horaAtencion").value("-"))
                .andExpect(jsonPath("$.turnos[9].codigo").value("T-009"));
    }

    @Test
    void postCreatesNextTicketWithQueuePosition() throws Exception {
        mvc.perform(post(BASE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.codigo").value("T-011"))
                .andExpect(jsonPath("$.numero").value(11))
                .andExpect(jsonPath("$.horaAtencion").value(nullValue()))
                .andExpect(jsonPath("$.estado").value("Pendiente"))
                .andExpect(jsonPath("$.posicionFila").value(6));
    }

    @Test
    void siguienteAttendsInFifoOrderThenReturns404WhenEmpty() throws Exception {
        for (String codigo : new String[]{"T-006", "T-007", "T-008", "T-009", "T-010"}) {
            mvc.perform(patch(BASE + "/siguiente"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.codigo").value(codigo))
                    .andExpect(jsonPath("$.estado").value("Atendido"));
        }

        mvc.perform(patch(BASE + "/siguiente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("FILA_VACIA"))
                .andExpect(jsonPath("$.mensaje").value("No hay personas esperando en la fila."));
    }

    @Test
    void cancelPendingTicket() throws Exception {
        mvc.perform(post(BASE));

        mvc.perform(patch(BASE + "/12/cancelar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.estado").value("Cancelado"))
                .andExpect(jsonPath("$.mensaje").value("Tu turno ha sido cancelado exitosamente."));

        mvc.perform(get(BASE + "/dashboard"))
                .andExpect(jsonPath("$.metricas.pendientes").value(5))
                .andExpect(jsonPath("$.metricas.cancelados").value(2));
    }

    @Test
    void cancelAttendedOrCancelledTicketReturns400() throws Exception {
        mvc.perform(patch(BASE + "/1/cancelar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("OPERACION_INVALIDA"));
        mvc.perform(patch(BASE + "/7/cancelar"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelUnknownTicketReturns404() throws Exception {
        mvc.perform(patch(BASE + "/999/cancelar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("TURNO_NO_ENCONTRADO"));
    }
}
