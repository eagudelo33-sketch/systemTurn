package com.systemturn.turnos.infrastructure.persistence;

import com.systemturn.turnos.application.TurnoService;
import com.systemturn.turnos.domain.Turno;
import com.systemturn.turnos.testsupport.EnabledIfDockerOrRequired;
import com.systemturn.turnos.testsupport.PostgresSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The whole H2 repository contract (inherited) against a real PostgreSQL container, plus the scenarios that only make
 * sense on PostgreSQL. Overrides exist only to give inherited cases their PostgreSQL scenario names.
 */
@EnabledIfDockerOrRequired
class JpaTurnoRepositoryPostgresTest extends JpaTurnoRepositoryTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static TimeZone zonaOriginal;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        PostgresSupport.register(registry);
    }

    /** The JVM zone must already be UTC when the context (and its first connection) is created. */
    @BeforeAll
    static void jvmEnUtc() {
        zonaOriginal = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterAll
    static void restaurarZona() {
        TimeZone.setDefault(zonaOriginal);
    }

    @Autowired
    Environment env;

    @Override
    @Test
    @DisplayName("Empty PostgreSQL database")
    void emptyDatabaseHasTheFlywaySchemaAndNoTickets() {
        super.emptyDatabaseHasTheFlywaySchemaAndNoTickets();
    }

    @Override
    @Test
    @DisplayName("Duplicate on PostgreSQL")
    void duplicateNumberOnTheSameDayIsTranslatedButAnotherDayIsFine() {
        super.duplicateNumberOnTheSameDayIsTranslatedButAnotherDayIsFine();
    }

    @Override
    @Test
    @DisplayName("Unrelated violation is not a duplicate (PostgreSQL)")
    void integrityViolationOnInsertThatIsNotTheUniqueNumberIsNotDisguisedAsDuplicate() {
        super.integrityViolationOnInsertThatIsNotTheUniqueNumberIsNotDisguisedAsDuplicate();
    }

    @Override
    @Test
    @DisplayName("Version conflict on PostgreSQL")
    void staleVersionIsTranslatedToConflictAndTheFirstWriteWins() {
        super.staleVersionIsTranslatedToConflictAndTheFirstWriteWins();
    }

    @Test
    @DisplayName("Duplicate on PostgreSQL: the engine reports the constraint name in lowercase")
    void duplicateOnPostgresqlReportsTheLowercaseConstraintName() {
        insertar(HOY, 1, LocalTime.of(9, 0));
        String insert = "insert into turno (fecha, numero, hora_solicitud, estado, version) values (?, 1, '09:01:00', 'PENDIENTE', 0)";

        assertThatThrownBy(() -> jdbc.update(insert, HOY))
                .isInstanceOf(DuplicateKeyException.class)
                .rootCause()
                .isInstanceOfSatisfying(PSQLException.class, e ->
                        assertThat(e.getServerErrorMessage().getConstraint()).isEqualTo("uk_turno_fecha_numero"));
    }

    @Test
    @DisplayName("Runs with Docker: V1 applied, validation passed, queries hit a PostgreSQL container")
    void runsWithDocker() {
        assertThat(PostgresSupport.container().isRunning()).isTrue();
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL 17.");
        assertThat(env.getProperty("spring.datasource.url")).startsWith("jdbc:postgresql:");
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(jdbc.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"version\" = '1' and \"success\" = true",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_constraint where conname = 'uk_turno_fecha_numero'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Time precision on PostgreSQL: 08:34:27 in Bogota with the JVM zone in UTC")
    void timePrecisionOnPostgresql() {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-29T13:34:27Z"), BOGOTA);
        assertThat(ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds()).isZero();

        Turno guardado = new TurnoService(repo, reloj, 10).solicitar();

        Turno leido = repo.findById(guardado.getId()).orElseThrow();
        assertThat(leido.getHoraSolicitud()).isEqualTo(LocalTime.of(8, 34, 27));
        assertThat(leido.getFecha()).isEqualTo(LocalDate.of(2026, 10, 29));
    }

    @Test
    @DisplayName("Time precision on PostgreSQL: the Bogota date wins over the UTC date near midnight")
    void timePrecisionKeepsTheBogotaDateNearMidnight() {
        // 04:30:15Z on the 30th is still 23:30:15 on the 29th in Bogota.
        Clock reloj = Clock.fixed(Instant.parse("2026-10-30T04:30:15Z"), BOGOTA);

        Turno guardado = new TurnoService(repo, reloj, 10).solicitar();

        Turno leido = repo.findById(guardado.getId()).orElseThrow();
        assertThat(leido.getHoraSolicitud()).isEqualTo(LocalTime.of(23, 30, 15));
        assertThat(leido.getFecha()).isEqualTo(LocalDate.of(2026, 10, 29));
    }
}
