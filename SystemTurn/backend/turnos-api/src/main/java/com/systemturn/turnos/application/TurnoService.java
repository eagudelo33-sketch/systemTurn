package com.systemturn.turnos.application;

import com.systemturn.turnos.domain.ConflictoException;
import com.systemturn.turnos.domain.EstadoTurno;
import com.systemturn.turnos.domain.FilaVaciaException;
import com.systemturn.turnos.domain.NumeroDuplicadoException;
import com.systemturn.turnos.domain.Turno;
import com.systemturn.turnos.domain.TurnoNoEncontradoException;
import com.systemturn.turnos.domain.TurnoRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Queue rules: FIFO by request time, next number = max + 1, average wait only over attended tickets.
 * Everything is scoped to "today" in the injected clock's zone. Concurrency is resolved by the repository
 * (unique numbering, versioned updates), not by locks: number assignment retries a bounded number of times and
 * stale writes surface as {@link ConflictoException}.
 */
public class TurnoService {

    private static final Comparator<Turno> FIFO =
            Comparator.comparing(Turno::getHoraSolicitud).thenComparing(Turno::getId);

    private final TurnoRepository repository;
    private final Clock clock;
    private final int maxIntentos;

    public TurnoService(TurnoRepository repository, Clock clock, int maxIntentos) {
        this.repository = repository;
        this.clock = clock;
        this.maxIntentos = maxIntentos;
    }

    public Turno solicitar() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDate hoy = ahora.toLocalDate();
        for (int intento = 0; intento < maxIntentos; intento++) {
            try {
                return repository.guardar(Turno.nuevo(hoy, repository.maxNumero(hoy) + 1, ahora.toLocalTime()));
            } catch (NumeroDuplicadoException e) {
                // another request took this number first: recompute max + 1 and try again
            }
        }
        throw new ConflictoException();
    }

    public Turno llamarSiguiente() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        Turno turno = fila(ahora.toLocalDate()).stream().findFirst().orElseThrow(FilaVaciaException::new);
        turno.atender(ahora.toLocalTime());
        return repository.guardar(turno);
    }

    public Turno cancelar(long id) {
        LocalDate hoy = LocalDate.now(clock);
        Turno turno = repository.findById(id).orElseThrow(TurnoNoEncontradoException::new);
        turno.cancelar(hoy);
        return repository.guardar(turno);
    }

    public List<Turno> fila() {
        return fila(LocalDate.now(clock));
    }

    private List<Turno> fila(LocalDate fecha) {
        return pendientesEnOrden(repository.findByFecha(fecha));
    }

    private static List<Turno> pendientesEnOrden(List<Turno> turnos) {
        return turnos.stream()
                .filter(t -> t.getEstado() == EstadoTurno.PENDIENTE)
                .sorted(FIFO)
                .toList();
    }

    public int posicion(Turno turno) {
        return fila().indexOf(turno) + 1;
    }

    public Dashboard dashboard() {
        List<Turno> todos = repository.findByFecha(LocalDate.now(clock));
        List<Turno> fila = pendientesEnOrden(todos);
        return new Dashboard(
                fila.size(),
                contar(todos, EstadoTurno.ATENDIDO),
                contar(todos, EstadoTurno.CANCELADO),
                esperaPromedioMin(todos),
                fila.isEmpty() ? null : fila.get(0),
                fila,
                todos.stream().sorted(Comparator.comparing(Turno::getId)).toList());
    }

    private static long contar(List<Turno> turnos, EstadoTurno estado) {
        return turnos.stream().filter(t -> t.getEstado() == estado).count();
    }

    private static double esperaPromedioMin(List<Turno> turnos) {
        double promedio = turnos.stream()
                .flatMap(t -> t.espera().stream())
                .mapToLong(Duration::toSeconds)
                .average()
                .orElse(0) / 60.0;
        return Math.round(promedio * 10) / 10.0;
    }
}
