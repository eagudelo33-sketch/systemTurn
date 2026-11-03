package com.systemturn.turnos.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

interface TurnoJpaRepository extends JpaRepository<TurnoJpaEntity, Long> {

    List<TurnoJpaEntity> findByFecha(LocalDate fecha);

    @Query("select coalesce(max(t.numero), 0) from TurnoJpaEntity t where t.fecha = :fecha")
    int maxNumero(@Param("fecha") LocalDate fecha);
}
