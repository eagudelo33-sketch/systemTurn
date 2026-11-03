package com.systemturn.turnos.domain;

/**
 * The persistence backend cannot be reached (connection acquisition or transaction start failed). The message is
 * deliberately generic: driver, URL and SQL details stay in the cause and in the logs, never in the API response.
 */
public class ServicioNoDisponibleException extends RuntimeException {
    public ServicioNoDisponibleException(Throwable causa) {
        super("Servicio no disponible, intenta de nuevo en unos minutos.", causa);
    }
}
