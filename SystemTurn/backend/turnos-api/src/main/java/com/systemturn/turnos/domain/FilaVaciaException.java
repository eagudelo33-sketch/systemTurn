package com.systemturn.turnos.domain;

public class FilaVaciaException extends RuntimeException {
    public FilaVaciaException() {
        super("No hay personas esperando en la fila.");
    }
}
