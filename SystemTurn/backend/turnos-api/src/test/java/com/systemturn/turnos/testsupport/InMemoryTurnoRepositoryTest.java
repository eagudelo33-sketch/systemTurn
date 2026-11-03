package com.systemturn.turnos.testsupport;

import com.systemturn.turnos.domain.ConflictoException;
import com.systemturn.turnos.domain.EstadoTurno;
import com.systemturn.turnos.domain.NumeroDuplicadoException;
import com.systemturn.turnos.domain.Turno;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryTurnoRepositoryTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 29);

    private final InMemoryTurnoRepository repo = new InMemoryTurnoRepository();

    private Turno insertar(LocalDate fecha, int numero) {
        return repo.guardar(Turno.nuevo(fecha, numero, LocalTime.of(9, 0)));
    }

    @Test
    void insertAssignsIdAndVersionAndScopesByDay() {
        Turno uno = insertar(HOY, 1);
        Turno dos = insertar(HOY, 2);
        insertar(HOY.minusDays(1), 9);

        assertThat(uno.getId()).isEqualTo(1L);
        assertThat(dos.getId()).isEqualTo(2L);
        assertThat(uno.getVersion()).isEqualTo(0L);
        assertThat(repo.findByFecha(HOY)).extracting(Turno::getNumero).containsExactly(1, 2);
        assertThat(repo.maxNumero(HOY)).isEqualTo(2);
        assertThat(repo.maxNumero(HOY.plusDays(1))).isZero();
    }

    @Test
    void readsReturnCopiesSoUnsavedMutationsAreInvisible() {
        Turno guardado = insertar(HOY, 1);

        Turno leido = repo.findById(guardado.getId()).orElseThrow();
        leido.cancelar(HOY);

        assertThat(repo.findById(guardado.getId()).orElseThrow().getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
        assertThat(repo.findByFecha(HOY).get(0)).isNotSameAs(leido).isEqualTo(leido);
    }

    @Test
    void updateBumpsVersionAndStaleWriteIsAConflict() {
        insertar(HOY, 1);
        Turno primero = repo.findById(1L).orElseThrow();
        Turno segundo = repo.findById(1L).orElseThrow();

        primero.cancelar(HOY);
        Turno actualizado = repo.guardar(primero);
        segundo.atender(LocalTime.of(9, 30));

        assertThat(actualizado.getVersion()).isEqualTo(1L);
        assertThatThrownBy(() -> repo.guardar(segundo)).isInstanceOf(ConflictoException.class);
        assertThat(repo.findById(1L).orElseThrow().getEstado()).isEqualTo(EstadoTurno.CANCELADO);
    }

    @Test
    void duplicateNumberOnTheSameDayIsRejectedButAnotherDayIsFine() {
        insertar(HOY, 1);

        assertThatThrownBy(() -> insertar(HOY, 1)).isInstanceOf(NumeroDuplicadoException.class);
        assertThat(insertar(HOY.plusDays(1), 1).getId()).isEqualTo(2L);
    }

    @Test
    void injectedFailureFiresOnceThenTheRepositoryRecovers() {
        repo.failNextGuardarWith(new NumeroDuplicadoException());

        assertThatThrownBy(() -> insertar(HOY, 1)).isInstanceOf(NumeroDuplicadoException.class);
        assertThat(repo.findByFecha(HOY)).isEmpty();
        assertThat(insertar(HOY, 1).getId()).isEqualTo(1L);
    }
}
