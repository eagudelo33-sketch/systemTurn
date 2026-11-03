package com.systemturn.turnos.infrastructure;

import com.systemturn.turnos.domain.EstadoTurno;
import com.systemturn.turnos.domain.Turno;
import com.systemturn.turnos.domain.TurnoRepository;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryTurnoRepository implements TurnoRepository {

    private final List<Turno> turnos = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong();

    public void load(int numero, LocalTime horaSolicitud, LocalTime horaAtencion, EstadoTurno estado) {
        turnos.add(new Turno(ids.incrementAndGet(), numero, horaSolicitud, horaAtencion, estado));
    }

    @Override
    public List<Turno> findAll() {
        return List.copyOf(turnos);
    }

    @Override
    public Optional<Turno> findById(long id) {
        return turnos.stream().filter(t -> t.getId() == id).findFirst();
    }

    @Override
    public Turno save(int numero, LocalTime horaSolicitud) {
        Turno turno = new Turno(ids.incrementAndGet(), numero, horaSolicitud, null, EstadoTurno.PENDIENTE);
        turnos.add(turno);
        return turno;
    }
}
