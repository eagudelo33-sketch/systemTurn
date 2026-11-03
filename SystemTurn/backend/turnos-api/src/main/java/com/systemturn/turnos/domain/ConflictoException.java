package com.systemturn.turnos.domain;

public class ConflictoException extends RuntimeException {
    public ConflictoException() {
        super("El turno cambió mientras se procesaba. Actualiza e intenta de nuevo.");
    }
}
