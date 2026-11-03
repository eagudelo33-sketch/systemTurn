package com.systemturn.turnos.infrastructure.persistence;

import com.systemturn.turnos.domain.EstadoTurno;
import com.systemturn.turnos.domain.Turno;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Persistence model of a {@link Turno}, kept separate so the domain stays free of JPA. The version is a wrapper
 * type on purpose: Spring Data decides insert vs. merge from {@code version == null}.
 */
@Entity
@Table(name = "turno")
class TurnoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private int numero;

    @Column(name = "hora_solicitud", nullable = false)
    private LocalTime horaSolicitud;

    @Column(name = "hora_atencion")
    private LocalTime horaAtencion;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private EstadoTurno estado;

    @Version
    private Long version;

    protected TurnoJpaEntity() {
    }

    static TurnoJpaEntity desde(Turno turno) {
        TurnoJpaEntity entity = new TurnoJpaEntity();
        entity.id = turno.getId();
        entity.fecha = turno.getFecha();
        entity.numero = turno.getNumero();
        entity.horaSolicitud = turno.getHoraSolicitud();
        entity.horaAtencion = turno.getHoraAtencion();
        entity.estado = turno.getEstado();
        entity.version = turno.getVersion();
        return entity;
    }

    Turno aDominio() {
        return new Turno(id, fecha, numero, horaSolicitud, horaAtencion, estado, version);
    }
}
