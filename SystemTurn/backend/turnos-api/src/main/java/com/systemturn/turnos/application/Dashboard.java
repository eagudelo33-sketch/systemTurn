package com.systemturn.turnos.application;

import com.systemturn.turnos.domain.Turno;

import java.util.List;

public record Dashboard(
        long pendientes,
        long atendidos,
        long cancelados,
        double esperaPromedioMin,
        Turno siguiente,
        List<Turno> fila,
        List<Turno> turnos) {
}
