package com.systemturn.turnos.infrastructure;

import com.systemturn.turnos.domain.EstadoTurno;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;

/**
 * Loads turnos_hoy.csv (numero,hora_solicitud,hora_atencion,estado) into the repository.
 * Rows are sorted by request time first, so internal ids follow arrival order as in the API contract.
 */
public final class CsvSeedLoader {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("H:mm");

    private CsvSeedLoader() {
    }

    private record Fila(int numero, LocalTime horaSolicitud, LocalTime horaAtencion, EstadoTurno estado) {
    }

    public static void load(Resource csv, InMemoryTurnoRepository repository) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csv.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines()
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(CsvSeedLoader::parse)
                    .sorted(Comparator.comparing(Fila::horaSolicitud))
                    .forEach(f -> repository.load(f.numero(), f.horaSolicitud(), f.horaAtencion(), f.estado()));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read seed file " + csv, e);
        }
    }

    private static Fila parse(String line) {
        String[] cols = line.split(",", -1);
        return new Fila(
                Integer.parseInt(cols[0].trim()),
                LocalTime.parse(cols[1].trim(), HORA),
                cols[2].isBlank() ? null : LocalTime.parse(cols[2].trim(), HORA),
                EstadoTurno.valueOf(cols[3].trim().toUpperCase(Locale.ROOT)));
    }
}
