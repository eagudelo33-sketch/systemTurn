package com.systemturn.turnos.web;

import com.systemturn.turnos.application.TurnoService;
import com.systemturn.turnos.domain.Turno;
import com.systemturn.turnos.web.Responses.CancelacionResponse;
import com.systemturn.turnos.web.Responses.DashboardResponse;
import com.systemturn.turnos.web.Responses.SolicitudResponse;
import com.systemturn.turnos.web.Responses.TurnoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/turnos")
public class TurnoController {

    private final TurnoService service;

    public TurnoController(TurnoService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return DashboardResponse.from(service.dashboard());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SolicitudResponse solicitar() {
        Turno turno = service.solicitar();
        return SolicitudResponse.from(turno, service.posicion(turno));
    }

    @PatchMapping("/siguiente")
    public TurnoResponse llamarSiguiente() {
        return TurnoResponse.from(service.llamarSiguiente());
    }

    @PatchMapping("/{id}/cancelar")
    public CancelacionResponse cancelar(@PathVariable long id) {
        return CancelacionResponse.from(service.cancelar(id));
    }
}
