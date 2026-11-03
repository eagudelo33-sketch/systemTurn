package com.systemturn.turnos.domain;

public class TurnoNoEncontradoException extends RuntimeException {
    public TurnoNoEncontradoException() {
        super("No se encontró el turno especificado.");
    }
}
