package com.systemturn.turnos.infrastructure;

import com.systemturn.turnos.domain.EstadoTurno;
import com.systemturn.turnos.domain.Turno;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pure parser for turnos_hoy.csv (numero,hora_solicitud,hora_atencion,estado). It persists nothing: it returns
 * unsaved tickets dated {@code fecha}, sorted by request time so that, once saved in order, internal ids follow
 * arrival order as in the API contract.
 */
public final class CsvSeedLoader {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("H:mm");

    private CsvSeedLoader() {
    }

    public static List<Turno> parse(Resource csv, LocalDate fecha) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csv.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(line -> fila(line, fecha))
                    .sorted(Comparator.comparing(Turno::getHoraSolicitud))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read seed file " + csv, e);
        }
    }

    private static Turno fila(String line, LocalDate fecha) {
        String[] cols = line.split(",", -1);
        return new Turno(
                null,
                fecha,
                Integer.parseInt(cols[0].trim()),
                LocalTime.parse(cols[1].trim(), HORA),
                cols[2].isBlank() ? null : LocalTime.parse(cols[2].trim(), HORA),
                EstadoTurno.valueOf(cols[3].trim().toUpperCase(Locale.ROOT)),
                null);
    }
}
