package com.systemturn.turnos.infrastructure.persistence;

import com.systemturn.turnos.domain.ConflictoException;
import com.systemturn.turnos.domain.EstadoTurno;
import com.systemturn.turnos.domain.NumeroDuplicadoException;
import com.systemturn.turnos.domain.Turno;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the adapter outside a test transaction, exactly as production does: every port call commits on its own,
 * so constraint and version failures surface inside the adapter where they are translated.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaTurnoRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaTurnoRepositoryTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 29);

    @Autowired
    private JpaTurnoRepository repo;

    @Autowired
    private TurnoJpaRepository jpa;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jpa.deleteAllInBatch();
    }

    private Turno insertar(LocalDate fecha, int numero, LocalTime hora) {
        return repo.guardar(Turno.nuevo(fecha, numero, hora));
    }

    @Test
    void emptyDatabaseHasTheFlywaySchemaAndNoTickets() {
        Integer migraciones = jdbc.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"version\" = '1' and \"success\" = true",
                Integer.class);

        assertThat(migraciones).isEqualTo(1);
        assertThat(repo.findByFecha(HOY)).isEmpty();
        assertThat(repo.maxNumero(HOY)).isZero();
        assertThat(repo.findById(1L)).isEmpty();
    }

    @Test
    void roundTripKeepsSecondsAndAssignsIdAndVersion() {
        Turno guardado = insertar(HOY, 1, LocalTime.of(9, 0, 37, 999_000_000));

        Turno leido = repo.findById(guardado.getId()).orElseThrow();

        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getVersion()).isEqualTo(0L);
        assertThat(leido.getHoraSolicitud()).isEqualTo(LocalTime.of(9, 0, 37));
        assertThat(leido.getFecha()).isEqualTo(HOY);
        assertThat(leido.getNumero()).isEqualTo(1);
        assertThat(leido.getEstado()).isEqualTo(EstadoTurno.PENDIENTE);
        assertThat(leido.getHoraAtencion()).isNull();
    }

    @Test
    void updatePersistsStateAttendanceTimeAndBumpsVersion() {
        Turno guardado = insertar(HOY, 1, LocalTime.of(9, 0));
        Turno leido = repo.findById(guardado.getId()).orElseThrow();
        leido.atender(LocalTime.of(9, 15, 42));

        Turno actualizado = repo.guardar(leido);

        Turno recargado = repo.findById(guardado.getId()).orElseThrow();
        assertThat(actualizado.getVersion()).isEqualTo(1L);
        assertThat(recargado.getEstado()).isEqualTo(EstadoTurno.ATENDIDO);
        assertThat(recargado.getHoraAtencion()).isEqualTo(LocalTime.of(9, 15, 42));
        assertThat(recargado.getVersion()).isEqualTo(1L);
    }

    @Test
    void findByFechaScopesByDayAndMaxNumeroTracksTheDay() {
        insertar(HOY, 1, LocalTime.of(9, 0));
        insertar(HOY, 2, LocalTime.of(9, 5));
        insertar(HOY.minusDays(1), 9, LocalTime.of(9, 0));

        assertThat(repo.findByFecha(HOY)).extracting(Turno::getNumero).containsExactlyInAnyOrder(1, 2);
        assertThat(repo.maxNumero(HOY)).isEqualTo(2);
        assertThat(repo.maxNumero(HOY.minusDays(1))).isEqualTo(9);
        assertThat(repo.maxNumero(HOY.plusDays(1))).isZero();
    }

    @Test
    void duplicateNumberOnTheSameDayIsTranslatedButAnotherDayIsFine() {
        insertar(HOY, 1, LocalTime.of(9, 0));

        assertThatThrownBy(() -> insertar(HOY, 1, LocalTime.of(9, 1)))
                .isInstanceOf(NumeroDuplicadoException.class);
        assertThat(insertar(HOY.plusDays(1), 1, LocalTime.of(9, 1)).getId()).isNotNull();
        assertThat(repo.findByFecha(HOY)).hasSize(1);
    }

    @Test
    void integrityViolationOnInsertThatIsNotTheUniqueNumberIsNotDisguisedAsDuplicate() {
        Turno sinEstado = new Turno(null, HOY, 1, LocalTime.of(9, 0), null, null, null);

        assertThatThrownBy(() -> repo.guardar(sinEstado))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(NumeroDuplicadoException.class);
        assertThat(repo.findByFecha(HOY)).isEmpty();
    }

    @Test
    void uniqueViolationOnUpdateIsNotDisguisedAsDuplicate() {
        insertar(HOY, 1, LocalTime.of(9, 0));
        Turno segundo = insertar(HOY, 2, LocalTime.of(9, 5));
        Turno renumerado = new Turno(segundo.getId(), HOY, 1, segundo.getHoraSolicitud(), null,
                EstadoTurno.PENDIENTE, segundo.getVersion());

        assertThatThrownBy(() -> repo.guardar(renumerado))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(NumeroDuplicadoException.class);
        assertThat(repo.findById(segundo.getId()).orElseThrow().getNumero()).isEqualTo(2);
    }

    @Test
    void staleVersionIsTranslatedToConflictAndTheFirstWriteWins() {
        Turno guardado = insertar(HOY, 1, LocalTime.of(9, 0));
        Turno primero = repo.findById(guardado.getId()).orElseThrow();
        Turno segundo = repo.findById(guardado.getId()).orElseThrow();

        primero.cancelar(HOY);
        repo.guardar(primero);
        segundo.atender(LocalTime.of(9, 30));

        assertThatThrownBy(() -> repo.guardar(segundo)).isInstanceOf(ConflictoException.class);
        Turno recargado = repo.findById(guardado.getId()).orElseThrow();
        assertThat(recargado.getEstado()).isEqualTo(EstadoTurno.CANCELADO);
        assertThat(recargado.getHoraAtencion()).isNull();
    }
}
