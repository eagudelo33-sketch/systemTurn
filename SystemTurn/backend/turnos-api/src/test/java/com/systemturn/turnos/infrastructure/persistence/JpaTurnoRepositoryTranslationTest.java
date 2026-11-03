package com.systemturn.turnos.infrastructure.persistence;

import com.systemturn.turnos.domain.ConflictoException;
import com.systemturn.turnos.domain.NumeroDuplicadoException;
import com.systemturn.turnos.domain.ServicioNoDisponibleException;
import com.systemturn.turnos.domain.Turno;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests of the adapter's exception translation, driven by fabricated exceptions so they do not depend on
 * any engine. The real engines are covered by {@link JpaTurnoRepositoryTest} (H2) and the Postgres suite.
 */
class JpaTurnoRepositoryTranslationTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 30);

    private final TurnoJpaRepository jpa = mock(TurnoJpaRepository.class);
    private final JpaTurnoRepository repo = new JpaTurnoRepository(jpa);

    private static Turno nuevo() {
        return Turno.nuevo(HOY, 1, LocalTime.of(9, 0));
    }

    private static DataIntegrityViolationException integridad(String causaMensaje, String constraint) {
        ConstraintViolationException hibernate = new ConstraintViolationException(
                causaMensaje, new SQLException(causaMensaje), constraint);
        return new DataIntegrityViolationException("could not execute statement", hibernate);
    }

    @Nested
    class DuplicateDetection {

        @ParameterizedTest
        @ValueSource(strings = {
                "UK_TURNO_FECHA_NUMERO",
                "uk_turno_fecha_numero",
                "Uk_Turno_Fecha_Numero",
                "\"uk_turno_fecha_numero\"",
                "public.uk_turno_fecha_numero",
                "PUBLIC.\"UK_TURNO_FECHA_NUMERO\"",
                "UK_TURNO_FECHA_NUMERO_INDEX_4"})
        void constraintNameIsMatchedCaseInsensitivelyInAnyQualifiedForm(String nombre) {
            when(jpa.saveAndFlush(any())).thenThrow(integridad("duplicate key", nombre));

            assertThatThrownBy(() -> repo.guardar(nuevo())).isInstanceOf(NumeroDuplicadoException.class);
        }

        @Test
        void hibernateExceptionDeepInTheCauseChainIsFound() {
            RuntimeException envuelta = new RuntimeException("wrapper",
                    new IllegalStateException("another",
                            new ConstraintViolationException("dup", new SQLException("dup"), "uk_turno_fecha_numero")));
            when(jpa.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("x", envuelta));

            assertThatThrownBy(() -> repo.guardar(nuevo())).isInstanceOf(NumeroDuplicadoException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Unique index or primary key violation: \"PUBLIC.UK_TURNO_FECHA_NUMERO_INDEX_4 ON PUBLIC.TURNO\"",
                "ERROR: duplicate key value violates unique constraint \"uk_turno_fecha_numero\""})
        void nullConstraintNameFallsBackToTheMessage(String mensaje) {
            when(jpa.saveAndFlush(any())).thenThrow(integridad(mensaje, null));

            assertThatThrownBy(() -> repo.guardar(nuevo())).isInstanceOf(NumeroDuplicadoException.class);
        }

        @Test
        void unrelatedConstraintNameIsNotADuplicate() {
            DataIntegrityViolationException original = integridad("null value in column estado", "turno_estado_check");
            when(jpa.saveAndFlush(any())).thenThrow(original);

            assertThatThrownBy(() -> repo.guardar(nuevo())).isSameAs(original);
        }

        @Test
        void unrelatedViolationWithoutNameOrMatchingMessageIsNotADuplicate() {
            DataIntegrityViolationException original = integridad("NULL not allowed for column \"ESTADO\"", null);
            when(jpa.saveAndFlush(any())).thenThrow(original);

            assertThatThrownBy(() -> repo.guardar(nuevo())).isSameAs(original);
        }

        @Test
        void violationWithoutAnyHibernateCauseAndNoMessageIsNotADuplicate() {
            DataIntegrityViolationException original = new DataIntegrityViolationException("boom");
            when(jpa.saveAndFlush(any())).thenThrow(original);

            assertThatThrownBy(() -> repo.guardar(nuevo())).isSameAs(original);
        }

        @Test
        void uniqueViolationOnUpdateIsNeverADuplicate() {
            DataIntegrityViolationException original = integridad("dup", "UK_TURNO_FECHA_NUMERO");
            when(jpa.saveAndFlush(any())).thenThrow(original);
            Turno existente = new Turno(5L, HOY, 1, LocalTime.of(9, 0), null,
                    com.systemturn.turnos.domain.EstadoTurno.PENDIENTE, 0L);

            assertThatThrownBy(() -> repo.guardar(existente)).isSameAs(original);
        }

        @Test
        void optimisticLockStillBecomesConflict() {
            when(jpa.saveAndFlush(any())).thenThrow(new ObjectOptimisticLockingFailureException(TurnoJpaEntity.class, 1L));

            assertThatThrownBy(() -> repo.guardar(nuevo())).isInstanceOf(ConflictoException.class);
        }
    }

    @Nested
    class DatabaseUnavailable {

        @Test
        void cannotCreateTransactionOnWriteBecomesServicioNoDisponible() {
            when(jpa.saveAndFlush(any())).thenThrow(new CannotCreateTransactionException("no tx"));

            assertThatThrownBy(() -> repo.guardar(nuevo())).isInstanceOf(ServicioNoDisponibleException.class);
        }

        @Test
        void connectionAcquisitionFailureOnWriteBecomesServicioNoDisponible() {
            when(jpa.saveAndFlush(any())).thenThrow(
                    new CannotGetJdbcConnectionException("jdbc:postgresql://db:5432/turnos refused", new SQLException("x")));

            assertThatThrownBy(() -> repo.guardar(nuevo()))
                    .isInstanceOf(ServicioNoDisponibleException.class)
                    .hasMessageNotContaining("jdbc");
        }

        @Test
        void resourceFailureBecomesServicioNoDisponibleOnEveryReadCall() {
            when(jpa.findByFecha(HOY)).thenThrow(new DataAccessResourceFailureException("down"));
            when(jpa.findById(1L)).thenThrow(new DataAccessResourceFailureException("down"));
            when(jpa.maxNumero(HOY)).thenThrow(new CannotCreateTransactionException("down"));

            assertThatThrownBy(() -> repo.findByFecha(HOY)).isInstanceOf(ServicioNoDisponibleException.class);
            assertThatThrownBy(() -> repo.findById(1L)).isInstanceOf(ServicioNoDisponibleException.class);
            assertThatThrownBy(() -> repo.maxNumero(HOY)).isInstanceOf(ServicioNoDisponibleException.class);
        }

        @Test
        void otherDataAccessExceptionsAreNotTranslated() {
            DataAccessException timeout = new QueryTimeoutException("slow query");
            when(jpa.maxNumero(HOY)).thenThrow(timeout);
            DataIntegrityViolationException integridad = integridad("NULL not allowed", null);
            when(jpa.saveAndFlush(any())).thenThrow(integridad);

            assertThatThrownBy(() -> repo.maxNumero(HOY)).isSameAs(timeout);
            assertThatThrownBy(() -> repo.guardar(nuevo())).isSameAs(integridad);
        }

        @Test
        void theOriginalCauseIsKeptForLogsButNotInTheMessage() {
            CannotCreateTransactionException causa = new CannotCreateTransactionException("jdbc:postgresql://db/x");
            when(jpa.maxNumero(HOY)).thenThrow(causa);

            assertThat(catchThrowableOf(() -> repo.maxNumero(HOY)))
                    .isInstanceOf(ServicioNoDisponibleException.class)
                    .hasCause(causa)
                    .hasMessageNotContaining("jdbc");
        }
    }

    private static Throwable catchThrowableOf(Runnable r) {
        try {
            r.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }
}
