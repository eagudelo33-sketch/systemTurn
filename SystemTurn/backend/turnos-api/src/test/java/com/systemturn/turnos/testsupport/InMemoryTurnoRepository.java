package com.systemturn.turnos.testsupport;

import com.systemturn.turnos.domain.ConflictoException;
import com.systemturn.turnos.domain.NumeroDuplicadoException;
import com.systemturn.turnos.domain.Turno;
import com.systemturn.turnos.domain.TurnoRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test fake with persistence-like semantics: reads and writes exchange COPIES (as a database would), updates are
 * version-checked, (fecha, numero) is unique, and one failure can be injected into the next {@code guardar}.
 */
public class InMemoryTurnoRepository implements TurnoRepository {

    private final List<Turno> almacen = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong();
    private RuntimeException fallo;

    /** The next {@code guardar} throws this exception once, without touching the stored data. */
    public synchronized void failNextGuardarWith(RuntimeException fallo) {
        this.fallo = fallo;
    }

    @Override
    public synchronized List<Turno> findByFecha(LocalDate fecha) {
        return almacen.stream().filter(t -> t.getFecha().equals(fecha)).map(InMemoryTurnoRepository::copia).toList();
    }

    @Override
    public synchronized Optional<Turno> findById(long id) {
        return almacen.stream().filter(t -> t.getId() == id).findFirst().map(InMemoryTurnoRepository::copia);
    }

    @Override
    public synchronized int maxNumero(LocalDate fecha) {
        return almacen.stream().filter(t -> t.getFecha().equals(fecha)).mapToInt(Turno::getNumero).max().orElse(0);
    }

    @Override
    public synchronized Turno guardar(Turno turno) {
        if (fallo != null) {
            RuntimeException inyectado = fallo;
            fallo = null;
            throw inyectado;
        }
        return turno.getId() == null ? insertar(turno) : actualizar(turno);
    }

    private Turno insertar(Turno turno) {
        boolean duplicado = almacen.stream()
                .anyMatch(t -> t.getFecha().equals(turno.getFecha()) && t.getNumero() == turno.getNumero());
        if (duplicado) {
            throw new NumeroDuplicadoException();
        }
        Turno guardado = new Turno(ids.incrementAndGet(), turno.getFecha(), turno.getNumero(),
                turno.getHoraSolicitud(), turno.getHoraAtencion(), turno.getEstado(), 0L);
        almacen.add(guardado);
        return copia(guardado);
    }

    private Turno actualizar(Turno turno) {
        int indice = indiceDe(turno.getId());
        if (indice < 0 || !almacen.get(indice).getVersion().equals(turno.getVersion())) {
            throw new ConflictoException();
        }
        Turno guardado = new Turno(turno.getId(), turno.getFecha(), turno.getNumero(), turno.getHoraSolicitud(),
                turno.getHoraAtencion(), turno.getEstado(), turno.getVersion() + 1);
        almacen.set(indice, guardado);
        return copia(guardado);
    }

    private int indiceDe(Long id) {
        for (int i = 0; i < almacen.size(); i++) {
            if (almacen.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static Turno copia(Turno t) {
        return new Turno(t.getId(), t.getFecha(), t.getNumero(), t.getHoraSolicitud(), t.getHoraAtencion(),
                t.getEstado(), t.getVersion());
    }
}
