package com.systemturn.turnos.infrastructure.persistence;

import com.systemturn.turnos.domain.ConflictoException;
import com.systemturn.turnos.domain.NumeroDuplicadoException;
import com.systemturn.turnos.domain.Turno;
import com.systemturn.turnos.domain.TurnoRepository;
import com.systemturn.turnos.domain.ServicioNoDisponibleException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

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
        return sinConexion(() -> jpa.findByFecha(fecha).stream().map(TurnoJpaEntity::aDominio).toList());
    }

    @Override
    public Optional<Turno> findById(long id) {
        return sinConexion(() -> jpa.findById(id).map(TurnoJpaEntity::aDominio));
    }

    @Override
    public int maxNumero(LocalDate fecha) {
        return sinConexion(() -> jpa.maxNumero(fecha));
    }

    @Override
    public Turno guardar(Turno turno) {
        return sinConexion(() -> guardarTraduciendo(turno));
    }

    /**
     * Translates ONLY "the database cannot be reached" failures (no transaction could start, no connection could be
     * acquired) into the domain exception; every other data-access failure keeps its own meaning.
     */
    private static <T> T sinConexion(Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (CannotCreateTransactionException | DataAccessResourceFailureException e) {
            throw new ServicioNoDisponibleException(e);
        }
    }

    private Turno guardarTraduciendo(Turno turno) {
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

    /**
     * Engine-independent: Hibernate's {@link ConstraintViolationException} carries the constraint name, which H2
     * reports in upper case and PostgreSQL in lower case (possibly quoted or schema-qualified). When no name is
     * available the driver message is the fallback.
     */
    private static boolean violaUnicoFechaNumero(Throwable e) {
        List<Throwable> cadena = cadenaDeCausas(e);
        for (Throwable t : cadena) {
            if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
                return contieneUnico(cve.getConstraintName());
            }
        }
        return cadena.stream().anyMatch(t -> contieneUnico(t.getMessage()));
    }

    /** The throwable and its causes in order, stopping at a cycle. */
    private static List<Throwable> cadenaDeCausas(Throwable e) {
        Set<Throwable> vistos = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Throwable> cadena = new ArrayList<>();
        for (Throwable t = e; t != null && vistos.add(t); t = t.getCause()) {
            cadena.add(t);
        }
        return cadena;
    }

    private static boolean contieneUnico(String texto) {
        return texto != null && texto.toUpperCase(Locale.ROOT).contains(UNICO_FECHA_NUMERO);
    }
}
