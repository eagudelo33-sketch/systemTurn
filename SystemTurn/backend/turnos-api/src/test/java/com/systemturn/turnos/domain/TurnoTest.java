package com.systemturn.turnos.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TurnoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 29);
    private static final LocalDate AYER = HOY.minusDays(1);

    private static Turno cargado(Long id, LocalDate fecha, EstadoTurno estado) {
        return new Turno(id, fecha, 3, LocalTime.of(9, 0), null, estado, 0L);
    }

    @Test
    void newTicketIsPendingWithoutIdOrVersion() {
        Turno turno = Turno.nuevo(HOY, 4, LocalTime.of(9, 15));

        assertThat(turno.getId()).isNull();
        assertThat(turno.getVersion()).isNull();
        assertThat(turno.getFecha()).isEqualTo(HOY);
        assertThat(turno.getNumero()).isEqualTo(4);
        assertThat(turno.getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
        assertThat(turno.getHoraAtencion()).isNull();
    }

    @Test
    void timesKeepSecondsPrecisionAndDropNanos() {
        Turno turno = new Turno(1L, HOY, 1, LocalTime.of(9, 0, 7, 500_000_000), LocalTime.of(9, 5, 9, 1),
                EstadoTurno.ATENDIDO, 0L);

        assertThat(turno.getHoraSolicitud()).isEqualTo(LocalTime.of(9, 0, 7));
        assertThat(turno.getHoraAtencion()).isEqualTo(LocalTime.of(9, 5, 9));
    }

    @Test
    void positionByEqualityFindsReloadedInstance() {
        Turno enMemoria = cargado(5L, HOY, EstadoTurno.PENDIENTE);
        Turno recargado = cargado(5L, HOY, EstadoTurno.PENDIENTE);
        List<Turno> fila = List.of(cargado(4L, HOY, EstadoTurno.PENDIENTE), recargado);

        assertThat(enMemoria).isEqualTo(recargado).hasSameHashCodeAs(recargado);
        assertThat(fila.indexOf(enMemoria) + 1).isEqualTo(2);
    }

    @Test
    void differentIdsAreNotEqualAndUnsavedTicketsUseIdentity() {
        assertThat(cargado(5L, HOY, EstadoTurno.PENDIENTE)).isNotEqualTo(cargado(6L, HOY, EstadoTurno.PENDIENTE));

        Turno a = Turno.nuevo(HOY, 1, LocalTime.of(9, 0));
        Turno b = Turno.nuevo(HOY, 1, LocalTime.of(9, 0));
        assertThat(a).isNotEqualTo(b).isEqualTo(a);
    }

    @Test
    void cancellingTodaysPendingTicketSucceeds() {
        Turno turno = cargado(1L, HOY, EstadoTurno.PENDIENTE);

        turno.cancelar(HOY);

        assertThat(turno.getEstado()).isEqualTo(EstadoTurno.CANCELADO);
    }

    @Test
    void cancellingAPastDayTicketIsInvalidAndLeavesItPending() {
        Turno turno = cargado(1L, AYER, EstadoTurno.PENDIENTE);

        assertThatThrownBy(() -> turno.cancelar(HOY)).isInstanceOf(OperacionInvalidaException.class);
        assertThat(turno.getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
    }

    @Test
    void cancellingANonPendingTicketOfTodayIsInvalid() {
        assertThatThrownBy(() -> cargado(1L, HOY, EstadoTurno.ATENDIDO).cancelar(HOY))
                .isInstanceOf(OperacionInvalidaException.class);
        assertThatThrownBy(() -> cargado(2L, HOY, EstadoTurno.CANCELADO).cancelar(HOY))
                .isInstanceOf(OperacionInvalidaException.class);
    }
}
