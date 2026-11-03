package com.systemturn.turnos.web;

import com.systemturn.turnos.application.Dashboard;
import com.systemturn.turnos.domain.Turno;

import java.util.List;

/** Response bodies, shaped exactly as the API contract (contrato v1.0.0). */
final class Responses {

    private Responses() {
    }

    record TurnoResponse(long id, String codigo, int numero, String horaSolicitud, String horaAtencion,
                         String estado) {
        static TurnoResponse from(Turno t) {
            return new TurnoResponse(t.getId(), t.codigo(), t.getNumero(), Hora.format(t.getHoraSolicitud()),
                    Hora.format(t.getHoraAtencion()), t.getEstado().etiqueta());
        }
    }

    record SolicitudResponse(long id, String codigo, int numero, String horaSolicitud, String horaAtencion,
                             String estado, int posicionFila) {
        static SolicitudResponse from(Turno t, int posicionFila) {
            return new SolicitudResponse(t.getId(), t.codigo(), t.getNumero(), Hora.format(t.getHoraSolicitud()),
                    Hora.format(t.getHoraAtencion()), t.getEstado().etiqueta(), posicionFila);
        }
    }

    record CancelacionResponse(long id, String codigo, int numero, String horaSolicitud, String horaAtencion,
                               String estado, String mensaje) {
        static CancelacionResponse from(Turno t) {
            return new CancelacionResponse(t.getId(), t.codigo(), t.getNumero(), Hora.format(t.getHoraSolicitud()),
                    Hora.format(t.getHoraAtencion()), t.getEstado().etiqueta(),
                    "Tu turno ha sido cancelado exitosamente.");
        }
    }

    record Metricas(long pendientes, long atendidos, long cancelados, double esperaPromedioMinutos) {
    }

    record SiguienteTurno(long id, String codigo, int numero, String horaSolicitud, String estado) {
        static SiguienteTurno from(Turno t) {
            return t == null ? null
                    : new SiguienteTurno(t.getId(), t.codigo(), t.getNumero(), Hora.format(t.getHoraSolicitud()),
                    t.getEstado().etiqueta());
        }
    }

    /** In the dashboard list the contract shows "-" for tickets not yet attended. */
    record DashboardResponse(Metricas metricas, SiguienteTurno siguienteTurno, List<TurnoResponse> turnos) {
        static DashboardResponse from(Dashboard d) {
            return new DashboardResponse(
                    new Metricas(d.pendientes(), d.atendidos(), d.cancelados(), d.esperaPromedioMin()),
                    SiguienteTurno.from(d.siguiente()),
                    d.turnos().stream().map(t -> {
                        TurnoResponse r = TurnoResponse.from(t);
                        return r.horaAtencion() != null ? r
                                : new TurnoResponse(r.id(), r.codigo(), r.numero(), r.horaSolicitud(), "-", r.estado());
                    }).toList());
        }
    }

    record ErrorResponse(String error, String mensaje) {
    }
}
