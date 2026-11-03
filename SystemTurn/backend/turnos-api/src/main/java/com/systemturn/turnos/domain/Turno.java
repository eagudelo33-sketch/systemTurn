package com.systemturn.turnos.domain;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;

/**
 * A ticket in the virtual queue. The internal id is decoupled from the visible number,
 * because the seed data reuses a number (7 cancelled, then 7 pending).
 */
public class Turno {

    private final long id;
    private final int numero;
    private final LocalTime horaSolicitud;
    private LocalTime horaAtencion;
    private EstadoTurno estado;

    public Turno(long id, int numero, LocalTime horaSolicitud, LocalTime horaAtencion, EstadoTurno estado) {
        this.id = id;
        this.numero = numero;
        this.horaSolicitud = horaSolicitud;
        this.horaAtencion = horaAtencion;
        this.estado = estado;
    }

    public void atender(LocalTime hora) {
        exigirPendiente();
        this.horaAtencion = hora;
        this.estado = EstadoTurno.ATENDIDO;
    }

    public void cancelar() {
        exigirPendiente();
        this.estado = EstadoTurno.CANCELADO;
    }

    private void exigirPendiente() {
        if (estado != EstadoTurno.PENDIENTE) {
            throw new OperacionInvalidaException();
        }
    }

    public Optional<Duration> espera() {
        if (estado != EstadoTurno.ATENDIDO || horaAtencion == null) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(horaSolicitud, horaAtencion));
    }

    public String codigo() {
        return String.format("T-%03d", numero);
    }

    public long getId() { return id; }
    public int getNumero() { return numero; }
    public LocalTime getHoraSolicitud() { return horaSolicitud; }
    public LocalTime getHoraAtencion() { return horaAtencion; }
    public EstadoTurno getEstado() { return estado; }
}
