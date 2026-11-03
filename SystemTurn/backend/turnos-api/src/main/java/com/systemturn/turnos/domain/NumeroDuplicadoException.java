package com.systemturn.turnos.domain;

public class NumeroDuplicadoException extends RuntimeException {
    public NumeroDuplicadoException() {
        super("Ya existe un turno con ese número para el día.");
    }
}
