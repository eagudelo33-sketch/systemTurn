package com.systemturn.turnos.infrastructure.persistence;

import com.systemturn.turnos.domain.ConflictoException;
import com.systemturn.turnos.domain.NumeroDuplicadoException;
import com.systemturn.turnos.domain.Turno;
import com.systemturn.turnos.domain.TurnoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Adapter of the {@link TurnoRepository} port over Spring Data JPA.
 * <p>
 * Intentionally NOT {@code @Transactional}: every call runs in Spring Data's own per-method transaction and
 * writes use {@code saveAndFlush}, so constraint and version failures are raised inside this class's
 * {@code try} blocks (not later at commit) and can be translated to domain exceptions.
 */
@Repository
public class JpaTurnoRepository implements TurnoRepository {

    /** Name given to the unique (fecha, numero) constraint in V1__create_turno.sql. */
    private static final String UNICO_FECHA_NUMERO = "UK_TURNO_FECHA_NUMERO";

    private final TurnoJpaRepository jpa;

    JpaTurnoRepository(TurnoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Turno> findByFecha(LocalDate fecha) {
        return jpa.findByFecha(fecha).stream().map(TurnoJpaEntity::aDominio).toList();
    }

    @Override
    public Optional<Turno> findById(long id) {
        return jpa.findById(id).map(TurnoJpaEntity::aDominio);
    }

    @Override
    public int maxNumero(LocalDate fecha) {
        return jpa.maxNumero(fecha);
    }

    @Override
    public Turno guardar(Turno turno) {
        try {
            return jpa.saveAndFlush(TurnoJpaEntity.desde(turno)).aDominio();
        } catch (DataIntegrityViolationException e) {
            // Only a new ticket colliding on (fecha, numero) is a duplicate number; any other violation
            // (NOT NULL, CHECK, a unique clash on update) is a different problem and is rethrown unchanged.
            if (turno.getId() == null && violaUnicoFechaNumero(e)) {
                throw new NumeroDuplicadoException();
            }
            throw e;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictoException();
        }
    }

    private static boolean violaUnicoFechaNumero(DataIntegrityViolationException e) {
        return e.getMostSpecificCause().getMessage() != null
                && e.getMostSpecificCause().getMessage().toUpperCase(Locale.ROOT).contains(UNICO_FECHA_NUMERO);
    }
}
