package com.systemturn.turnos.web;

import com.systemturn.turnos.domain.FilaVaciaException;
import com.systemturn.turnos.domain.OperacionInvalidaException;
import com.systemturn.turnos.domain.TurnoNoEncontradoException;
import com.systemturn.turnos.web.Responses.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(FilaVaciaException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse filaVacia(FilaVaciaException e) {
        return new ErrorResponse("FILA_VACIA", e.getMessage());
    }

    @ExceptionHandler(TurnoNoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse noEncontrado(TurnoNoEncontradoException e) {
        return new ErrorResponse("TURNO_NO_ENCONTRADO", e.getMessage());
    }

    @ExceptionHandler(OperacionInvalidaException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse operacionInvalida(OperacionInvalidaException e) {
        return new ErrorResponse("OPERACION_INVALIDA", e.getMessage());
    }
}
