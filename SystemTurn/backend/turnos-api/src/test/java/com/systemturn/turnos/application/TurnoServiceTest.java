package com.systemturn.turnos.application;

import com.systemturn.turnos.domain.ConflictoException;
import com.systemturn.turnos.domain.EstadoTurno;
import com.systemturn.turnos.domain.FilaVaciaException;
import com.systemturn.turnos.domain.NumeroDuplicadoException;
import com.systemturn.turnos.domain.OperacionInvalidaException;
import com.systemturn.turnos.domain.Turno;
import com.systemturn.turnos.domain.TurnoRepository;
import com.systemturn.turnos.domain.TurnoNoEncontradoException;
import com.systemturn.turnos.testsupport.InMemoryTurnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TurnoServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 29);
    private static final LocalDate AYER = HOY.minusDays(1);
    private static final int MAX_INTENTOS = 10;

    /** 09:00 on 2026-10-29 in Bogota (UTC-5). */
    private final Clock nineAm = bogota("2026-10-29T14:00:00Z");
    private InMemoryTurnoRepository repository;
    private TurnoService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTurnoRepository();
        sembrarDiaDeEjemplo(repository, HOY);
        service = new TurnoService(repository, nineAm, MAX_INTENTOS);
    }

    private static Clock bogota(String instant) {
        return Clock.fixed(Instant.parse(instant), BOGOTA);
    }

    private static void sembrar(TurnoRepository repo, LocalDate fecha, int numero, String solicitud,
                                String atencion, EstadoTurno estado) {
        repo.guardar(new Turno(null, fecha, numero, LocalTime.parse(solicitud),
                atencion == null ? null : LocalTime.parse(atencion), estado, null));
    }

    /** Same shape as the demo day (5 attended, 1 cancelled, 5 pending, request order differs from number order). */
    private static void sembrarDiaDeEjemplo(InMemoryTurnoRepository repo, LocalDate fecha) {
        sembrar(repo, fecha, 1, "08:02", "08:05", EstadoTurno.ATENDIDO);
        sembrar(repo, fecha, 2, "08:06", "08:15", EstadoTurno.ATENDIDO);
        sembrar(repo, fecha, 3, "08:10", "08:24", EstadoTurno.ATENDIDO);
        sembrar(repo, fecha, 4, "08:15", "08:31", EstadoTurno.ATENDIDO);
        sembrar(repo, fecha, 5, "08:21", "08:40", EstadoTurno.ATENDIDO);
        sembrar(repo, fecha, 6, "08:25", null, EstadoTurno.PENDIENTE);
        sembrar(repo, fecha, 7, "08:30", null, EstadoTurno.CANCELADO);
        sembrar(repo, fecha, 8, "08:34", null, EstadoTurno.PENDIENTE);
        sembrar(repo, fecha, 10, "08:38", null, EstadoTurno.PENDIENTE);
        sembrar(repo, fecha, 9, "08:41", null, EstadoTurno.PENDIENTE);
        sembrar(repo, fecha, 11, "08:45", null, EstadoTurno.PENDIENTE);
    }

    /** Repository whose inserts always collide on the ticket number. */
    private static class DuplicadoSiempre extends InMemoryTurnoRepository {
        int intentos;

        @Override
        public synchronized Turno guardar(Turno turno) {
            intentos++;
            throw new NumeroDuplicadoException();
        }
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
        assertThat(service.fila()).extracting(Turno::getNumero).containsExactly(6, 8, 10, 9, 11);
    }

    @Test
    void dashboardListsTicketsByInternalId() {
        assertThat(service.dashboard().turnos()).extracting(Turno::getId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
    }

    @Test
    void newTicketTakesMaxNumberPlusOneAndGoesLast() {
        Turno nuevo = service.solicitar();

        assertThat(nuevo.codigo()).isEqualTo("T-012");
        assertThat(nuevo.getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
        assertThat(nuevo.getFecha()).isEqualTo(HOY);
        assertThat(nuevo.getHoraSolicitud()).isEqualTo(LocalTime.of(9, 0));
        assertThat(service.posicion(nuevo)).isEqualTo(6);
        assertThat(service.solicitar().codigo()).isEqualTo("T-013");
    }

    @Test
    void ticketsKeepSecondPrecision() {
        TurnoService conSegundos = new TurnoService(repository, bogota("2026-10-29T14:00:37Z"), MAX_INTENTOS);

        Turno nuevo = conSegundos.solicitar();
        Turno atendido = conSegundos.llamarSiguiente();

        assertThat(nuevo.getHoraSolicitud()).isEqualTo(LocalTime.of(9, 0, 37));
        assertThat(atendido.getHoraAtencion()).isEqualTo(LocalTime.of(9, 0, 37));
    }

    @Test
    void callingNextAttendsTheOldestPendingTicket() {
        Turno atendido = service.llamarSiguiente();

        assertThat(atendido.codigo()).isEqualTo("T-006");
        assertThat(atendido.getEstado()).isEqualTo(EstadoTurno.ATENDIDO);
        assertThat(atendido.getHoraAtencion()).isEqualTo(LocalTime.of(9, 0));
        assertThat(service.dashboard().siguiente().codigo()).isEqualTo("T-008");
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
    void cancelledTicketLeavesTheQueue() {
        Turno nuevo = service.solicitar();

        Turno cancelado = service.cancelar(nuevo.getId());

        assertThat(cancelado.getEstado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(service.fila()).doesNotContain(nuevo);
        assertThat(service.dashboard().cancelados()).isEqualTo(2);
    }

    @Test
    void positionIsComputedForAReloadedInstance() {
        Turno nuevo = service.solicitar();
        Turno recargado = repository.findById(nuevo.getId()).orElseThrow();

        assertThat(recargado).isNotSameAs(nuevo);
        assertThat(service.posicion(recargado)).isEqualTo(6);
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

    // --- daily queue ---

    @Test
    void midnightBoundaryUsesTheConfiguredZoneNotUtc() {
        // 2026-10-30T04:30Z is already the 30th in UTC but still 23:30 on the 29th in Bogota.
        TurnoService casiMedianoche = new TurnoService(repository, bogota("2026-10-30T04:30:00Z"), MAX_INTENTOS);

        Turno nuevo = casiMedianoche.solicitar();

        assertThat(nuevo.getFecha()).isEqualTo(HOY);
        assertThat(nuevo.codigo()).isEqualTo("T-012");
        assertThat(nuevo.getHoraSolicitud()).isEqualTo(LocalTime.of(23, 30));
        assertThat(casiMedianoche.dashboard().turnos()).hasSize(12);
    }

    @Test
    void afterMidnightInTheZoneANewDayStarts() {
        TurnoService diaSiguiente = new TurnoService(repository, bogota("2026-10-30T05:30:00Z"), MAX_INTENTOS);

        assertThat(diaSiguiente.dashboard().turnos()).isEmpty();
        assertThat(diaSiguiente.solicitar().getFecha()).isEqualTo(HOY.plusDays(1));
    }

    @Test
    void yesterdaysTicketsAreExcluded() {
        TurnoService manana = new TurnoService(repository, bogota("2026-10-30T14:00:00Z"), MAX_INTENTOS);
        sembrar(repository, HOY.plusDays(1), 1, "08:00", null, EstadoTurno.PENDIENTE);

        Dashboard d = manana.dashboard();

        assertThat(d.turnos()).extracting(Turno::getFecha).containsOnly(HOY.plusDays(1));
        assertThat(d.turnos()).hasSize(1);
        assertThat(d.pendientes()).isEqualTo(1);
        assertThat(d.atendidos()).isZero();
        assertThat(manana.fila()).extracting(Turno::codigo).containsExactly("T-001");
    }

    @Test
    void firstTicketOfTheDayRestartsAtOne() {
        TurnoService manana = new TurnoService(repository, bogota("2026-10-30T14:00:00Z"), MAX_INTENTOS);

        Turno primero = manana.solicitar();

        assertThat(primero.codigo()).isEqualTo("T-001");
        assertThat(primero.getFecha()).isEqualTo(HOY.plusDays(1));
        assertThat(manana.solicitar().codigo()).isEqualTo("T-002");
    }

    @Test
    void cancellingYesterdaysTicketIsInvalid() {
        TurnoService manana = new TurnoService(repository, bogota("2026-10-30T14:00:00Z"), MAX_INTENTOS);
        long pendienteDeAyer = repository.findByFecha(HOY).stream()
                .filter(t -> t.getEstado() == EstadoTurno.PENDIENTE).findFirst().orElseThrow().getId();

        assertThatThrownBy(() -> manana.cancelar(pendienteDeAyer)).isInstanceOf(OperacionInvalidaException.class);
        assertThat(repository.findById(pendienteDeAyer).orElseThrow().getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
    }

    // --- concurrency conflicts ---

    @Test
    void solicitarRetriesAfterADuplicateNumber() {
        repository.failNextGuardarWith(new NumeroDuplicadoException());

        Turno nuevo = service.solicitar();

        assertThat(nuevo.codigo()).isEqualTo("T-012");
        assertThat(repository.findByFecha(HOY)).hasSize(12);
    }

    @Test
    void retryExhaustionEndsInConflicto() {
        DuplicadoSiempre siempreDuplica = new DuplicadoSiempre();
        TurnoService limitado = new TurnoService(siempreDuplica, nineAm, 2);

        assertThatThrownBy(limitado::solicitar).isInstanceOf(ConflictoException.class);
        assertThat(siempreDuplica.intentos).isEqualTo(2);
    }

    @Test
    void cancelAfterACompletedAttendIsNotAConflict() {
        Turno nuevo = service.solicitar();
        for (int i = 0; i < 6; i++) {
            service.llamarSiguiente();
        }
        assertThat(repository.findById(nuevo.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.ATENDIDO);

        assertThatThrownBy(() -> service.cancelar(nuevo.getId()))
                .isInstanceOf(OperacionInvalidaException.class)
                .isNotInstanceOf(ConflictoException.class);
    }

    @Test
    void staleWriteWhileCallingNextSurfacesAsConflicto() {
        repository.failNextGuardarWith(new ConflictoException());

        assertThatThrownBy(service::llamarSiguiente).isInstanceOf(ConflictoException.class);
        assertThat(service.dashboard().siguiente().codigo()).isEqualTo("T-006");
    }

    @Test
    void staleWriteWhileCancellingSurfacesAsConflicto() {
        Turno nuevo = service.solicitar();
        repository.failNextGuardarWith(new ConflictoException());

        assertThatThrownBy(() -> service.cancelar(nuevo.getId())).isInstanceOf(ConflictoException.class);
        assertThat(repository.findById(nuevo.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
    }
}
