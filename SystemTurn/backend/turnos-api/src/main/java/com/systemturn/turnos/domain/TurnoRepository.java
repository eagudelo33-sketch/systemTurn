package com.systemturn.turnos.domain;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface TurnoRepository {
    List<Turno> findAll();

    Optional<Turno> findById(long id);

    Turno save(int numero, LocalTime horaSolicitud);
}
