package com.systemturn.turnos.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TurnoRepository {
    List<Turno> findByFecha(LocalDate fecha);

    Optional<Turno> findById(long id);

    /** Highest ticket number of the day, 0 when the day is empty. */
    int maxNumero(LocalDate fecha);

    /**
     * Inserts when the ticket has no id, otherwise performs a versioned update.
     *
     * @throws NumeroDuplicadoException when the (fecha, numero) pair already exists
     * @throws ConflictoException       when the ticket changed since it was read
     */
    Turno guardar(Turno turno);
}
