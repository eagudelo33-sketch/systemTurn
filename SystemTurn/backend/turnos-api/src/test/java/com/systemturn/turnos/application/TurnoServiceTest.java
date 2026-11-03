package com.systemturn.turnos.application;

import com.systemturn.turnos.domain.EstadoTurno;
import com.systemturn.turnos.domain.FilaVaciaException;
import com.systemturn.turnos.domain.OperacionInvalidaException;
import com.systemturn.turnos.domain.Turno;
import com.systemturn.turnos.domain.TurnoNoEncontradoException;
import com.systemturn.turnos.infrastructure.CsvSeedLoader;
import com.systemturn.turnos.infrastructure.InMemoryTurnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class TurnoServiceTest {

    private final Clock nineAm = Clock.fixed(Instant.parse("2026-10-29T09:00:00Z"), ZoneOffset.UTC);
    private TurnoService service;

    @BeforeEach
    void setUp() {
        InMemoryTurnoRepository repository = new InMemoryTurnoRepository();
        CsvSeedLoader.load(new ClassPathResource("turnos_hoy.csv"), repository);
        service = new TurnoService(repository, nineAm);
    }

    @Test
    void dashboardMatchesSeedStatistics() {
        Dashboard d = service.dashboard();

        assertThat(d.pendientes()).isEqualTo(5);
        assertThat(d.atendidos()).isEqualTo(5);
        assertThat(d.cancelados()).isEqualTo(1);
        assertThat(d.esperaPromedioMin()).isEqualTo(12.2);
        assertThat(d.siguiente().codigo()).isEqualTo("T-006");
        assertThat(d.turnos()).hasSize(11);
    }

    @Test
    void queueIsOrderedByRequestTimeNotByNumber() {
        assertThat(service.fila()).extracting(Turno::getNumero).containsExactly(6, 7, 8, 9, 10);
    }

    @Test
    void newTicketTakesMaxNumberPlusOneAndGoesLast() {
        Turno nuevo = service.solicitar();

        assertThat(nuevo.codigo()).isEqualTo("T-011");
        assertThat(nuevo.getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
        assertThat(nuevo.getHoraSolicitud()).isEqualTo(LocalTime.of(9, 0));
        assertThat(service.posicion(nuevo)).isEqualTo(6);
        assertThat(service.solicitar().codigo()).isEqualTo("T-012");
    }

    @Test
    void callingNextAttendsTheOldestPendingTicket() {
        Turno atendido = service.llamarSiguiente();

        assertThat(atendido.codigo()).isEqualTo("T-006");
        assertThat(atendido.getEstado()).isEqualTo(EstadoTurno.ATENDIDO);
        assertThat(atendido.getHoraAtencion()).isEqualTo(LocalTime.of(9, 0));
        assertThat(service.dashboard().siguiente().codigo()).isEqualTo("T-007");
        assertThat(service.dashboard().atendidos()).isEqualTo(6);
    }

    @Test
    void averageWaitIgnoresPendingAndCancelledTickets() {
        service.solicitar();

        assertThat(service.dashboard().esperaPromedioMin()).isEqualTo(12.2);
    }

    @Test
    void callingNextOnEmptyQueueFailsWithFilaVacia() {
        for (int i = 0; i < 5; i++) {
            service.llamarSiguiente();
        }

        assertThatThrownBy(service::llamarSiguiente).isInstanceOf(FilaVaciaException.class);
        assertThat(service.dashboard().siguiente()).isNull();
    }

    @Test
    void seedIdsFollowArrivalOrder() {
        assertThat(service.dashboard().turnos())
                .extracting(Turno::getId, Turno::getNumero)
                .startsWith(tuple(1L, 1))
                .contains(tuple(7L, 7), tuple(8L, 7), tuple(10L, 9), tuple(11L, 10));
    }

    @Test
    void cancelledTicketLeavesTheQueue() {
        Turno nuevo = service.solicitar();

        service.cancelar(nuevo.getId());

        assertThat(nuevo.getEstado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(service.fila()).doesNotContain(nuevo);
        assertThat(service.dashboard().cancelados()).isEqualTo(2);
    }

    @Test
    void cannotCancelAttendedOrAlreadyCancelledTickets() {
        assertThatThrownBy(() -> service.cancelar(1)).isInstanceOf(OperacionInvalidaException.class);
        assertThatThrownBy(() -> service.cancelar(7)).isInstanceOf(OperacionInvalidaException.class);
    }

    @Test
    void cancellingUnknownTicketFails() {
        assertThatThrownBy(() -> service.cancelar(999)).isInstanceOf(TurnoNoEncontradoException.class);
    }
}
