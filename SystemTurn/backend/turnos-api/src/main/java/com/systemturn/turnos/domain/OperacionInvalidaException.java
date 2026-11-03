package com.systemturn.turnos.domain;

public class OperacionInvalidaException extends RuntimeException {
    public OperacionInvalidaException() {
        super("No es posible cancelar un turno que ya ha sido atendido o cancelado.");
    }
}
