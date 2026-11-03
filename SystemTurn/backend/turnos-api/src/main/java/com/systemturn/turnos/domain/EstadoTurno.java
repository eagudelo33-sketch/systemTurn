package com.systemturn.turnos.domain;

public enum EstadoTurno {
    PENDIENTE("Pendiente"),
    ATENDIDO("Atendido"),
    CANCELADO("Cancelado");

    private final String etiqueta;

    EstadoTurno(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
