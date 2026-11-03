package com.systemturn.turnos.web;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Formats times as the contract expects, e.g. "08:25 a. m." / "02:10 p. m.". */
final class Hora {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("hh:mm");

    private Hora() {
    }

    static String format(LocalTime hora) {
        if (hora == null) {
            return null;
        }
        return hora.format(HH_MM) + (hora.getHour() < 12 ? " a. m." : " p. m.");
    }
}
