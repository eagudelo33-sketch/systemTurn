package com.systemturn.turnos.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * A ticket in the virtual queue, belonging to one day ({@code fecha}). The internal id is decoupled from the
 * visible number. Id and version are null until the ticket is first saved; the version is opaque to the domain
 * and only used by the repository to detect stale writes. Equality is by id (identity while unsaved).
 */
public class Turno {

    private final Long id;
    private final LocalDate fecha;
    private final int numero;
    private final LocalTime horaSolicitud;
    private LocalTime horaAtencion;
    private EstadoTurno estado;
    private final Long version;

    public Turno(Long id, LocalDate fecha, int numero, LocalTime horaSolicitud, LocalTime horaAtencion,
                 EstadoTurno estado, Long version) {
        this.id = id;
        this.fecha = fecha;
        this.numero = numero;
        this.horaSolicitud = segundos(horaSolicitud);
        this.horaAtencion = segundos(horaAtencion);
        this.estado = estado;
        this.version = version;
    }

    public static Turno nuevo(LocalDate fecha, int numero, LocalTime hora) {
        return new Turno(null, fecha, numero, hora, null, EstadoTurno.PENDIENTE, null);
    }

    public void atender(LocalTime hora) {
        exigirPendiente();
        this.horaAtencion = segundos(hora);
        this.estado = EstadoTurno.ATENDIDO;
    }

    /** Only a pending ticket of the current day can be cancelled. */
    public void cancelar(LocalDate hoy) {
        exigirPendiente();
        if (!fecha.equals(hoy)) {
            throw new OperacionInvalidaException();
        }
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

    private static LocalTime segundos(LocalTime hora) {
        return hora == null ? null : hora.truncatedTo(ChronoUnit.SECONDS);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Turno otro && id != null && id.equals(otro.id);
    }

    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : Objects.hash(id);
    }

    public Long getId() { return id; }
    public LocalDate getFecha() { return fecha; }
    public int getNumero() { return numero; }
    public LocalTime getHoraSolicitud() { return horaSolicitud; }
    public LocalTime getHoraAtencion() { return horaAtencion; }
    public EstadoTurno getEstado() { return estado; }
    public Long getVersion() { return version; }
}
