package com.systemturn.turnos.application;

import com.systemturn.turnos.domain.EstadoTurno;
import com.systemturn.turnos.domain.FilaVaciaException;
import com.systemturn.turnos.domain.Turno;
import com.systemturn.turnos.domain.TurnoNoEncontradoException;
import com.systemturn.turnos.domain.TurnoRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Queue rules: FIFO by request time, next number = max + 1, average wait only over attended tickets.
 * Methods are synchronized so concurrent requests cannot hand out the same number or call the same ticket.
 */
public class TurnoService {

    private static final Comparator<Turno> FIFO =
            Comparator.comparing(Turno::getHoraSolicitud).thenComparingLong(Turno::getId);

    private final TurnoRepository repository;
    private final Clock clock;

    public TurnoService(TurnoRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public synchronized Turno solicitar() {
        int siguienteNumero = repository.findAll().stream()
                .mapToInt(Turno::getNumero)
                .max()
                .orElse(0) + 1;
        return repository.save(siguienteNumero, ahora());
    }

    public synchronized Turno llamarSiguiente() {
        Turno turno = fila().stream().findFirst().orElseThrow(FilaVaciaException::new);
        turno.atender(ahora());
        return turno;
    }

    public synchronized Turno cancelar(long id) {
        Turno turno = repository.findById(id).orElseThrow(TurnoNoEncontradoException::new);
        turno.cancelar();
        return turno;
    }

    public synchronized List<Turno> fila() {
        return repository.findAll().stream()
                .filter(t -> t.getEstado() == EstadoTurno.PENDIENTE)
                .sorted(FIFO)
                .toList();
    }

    public synchronized int posicion(Turno turno) {
        return fila().indexOf(turno) + 1;
    }

    public synchronized Dashboard dashboard() {
        List<Turno> todos = repository.findAll();
        List<Turno> fila = fila();
        return new Dashboard(
                fila.size(),
                contar(todos, EstadoTurno.ATENDIDO),
                contar(todos, EstadoTurno.CANCELADO),
                esperaPromedioMin(todos),
                fila.isEmpty() ? null : fila.get(0),
                fila,
                todos.stream().sorted(Comparator.comparingLong(Turno::getId)).toList());
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

    private LocalTime ahora() {
        return LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    }
}
