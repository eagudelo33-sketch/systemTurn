package com.systemturn.turnos.web;

import com.systemturn.turnos.application.TurnoService;
import com.systemturn.turnos.domain.ConflictoException;
import com.systemturn.turnos.domain.FilaVaciaException;
import com.systemturn.turnos.domain.OperacionInvalidaException;
import com.systemturn.turnos.domain.ServicioNoDisponibleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TurnoController.class)
class ConflictoHandlerTest {

    private static final String BASE = "/api/v1/turnos";
    private static final String MENSAJE =
            "El turno cambió mientras se procesaba. Actualiza e intenta de nuevo.";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TurnoService service;

    @Test
    void conflictOnLlamarSiguienteReturns409WithConflictoBody() throws Exception {
        when(service.llamarSiguiente()).thenThrow(new ConflictoException());

        mvc.perform(patch(BASE + "/siguiente"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICTO"))
                .andExpect(jsonPath("$.mensaje").value(MENSAJE));
    }

    @Test
    void conflictOnCancelarReturns409WithConflictoBody() throws Exception {
        when(service.cancelar(12L)).thenThrow(new ConflictoException());

        mvc.perform(patch(BASE + "/12/cancelar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICTO"))
                .andExpect(jsonPath("$.mensaje").value(MENSAJE));
    }

    @Test
    void conflictOnSolicitarReturns409WithConflictoBody() throws Exception {
        when(service.solicitar()).thenThrow(new ConflictoException());

        mvc.perform(post(BASE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICTO"));
    }

    private static final String MENSAJE_503 = "Servicio no disponible, intenta de nuevo en unos minutos.";

    @Test
    void solicitarWithTheDatabaseDownReturns503WithoutDriverDetails() throws Exception {
        when(service.solicitar()).thenThrow(new ServicioNoDisponibleException(
                new CannotGetJdbcConnectionException("jdbc:postgresql://db:5432/turnos refused")));

        mvc.perform(post(BASE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICIO_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_503))
                .andExpect(content().string(not(containsString("jdbc"))));
    }

    @Test
    void queueReadWithTheDatabaseDownReturns503() throws Exception {
        when(service.dashboard()).thenThrow(new ServicioNoDisponibleException(
                new DataAccessResourceFailureException("down")));

        mvc.perform(get(BASE + "/dashboard"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICIO_NO_DISPONIBLE"));
    }

    @Test
    void otherErrorShapesAreUnchanged() throws Exception {
        when(service.llamarSiguiente()).thenThrow(new FilaVaciaException());
        when(service.cancelar(1L)).thenThrow(new OperacionInvalidaException());

        mvc.perform(patch(BASE + "/siguiente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("FILA_VACIA"));
        mvc.perform(patch(BASE + "/1/cancelar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("OPERACION_INVALIDA"));
    }
}
