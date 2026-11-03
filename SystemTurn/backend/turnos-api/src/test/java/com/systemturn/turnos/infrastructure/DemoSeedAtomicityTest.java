package com.systemturn.turnos.infrastructure;

import com.systemturn.turnos.domain.NumeroDuplicadoException;
import com.systemturn.turnos.domain.Turno;
import com.systemturn.turnos.domain.TurnoRepository;
import com.systemturn.turnos.infrastructure.persistence.JpaTurnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The demo seed is all-or-nothing: when a row fails midway (here a REAL unique-constraint violation against H2),
 * no seed rows remain, so the next startup can seed the whole queue again.
 */
@SpringBootTest(properties = "turnos.test-context=demo-seed-atomicity")
@ActiveProfiles({"dev", "demo", "test"})
@Import(DemoSeedAtomicityTest.FailingRepositoryConfig.class)
class DemoSeedAtomicityTest {

    /** Makes the Nth {@code guardar} collide with row number 1, a genuine duplicate on the same day. */
    static class FailingOnNthSave implements TurnoRepository {
        private final TurnoRepository delegate;
        private final AtomicInteger failAt = new AtomicInteger(0);
        private final AtomicInteger calls = new AtomicInteger(0);

        FailingOnNthSave(TurnoRepository delegate) {
            this.delegate = delegate;
        }

        void failAt(int n) {
            calls.set(0);
            failAt.set(n);
        }

        @Override
        public List<Turno> findByFecha(LocalDate fecha) {
            return delegate.findByFecha(fecha);
        }

        @Override
        public Optional<Turno> findById(long id) {
            return delegate.findById(id);
        }

        @Override
        public int maxNumero(LocalDate fecha) {
            return delegate.maxNumero(fecha);
        }

        @Override
        public Turno guardar(Turno turno) {
            if (failAt.get() > 0 && calls.incrementAndGet() == failAt.get()) {
                return delegate.guardar(Turno.nuevo(turno.getFecha(), 1, turno.getHoraSolicitud()));
            }
            return delegate.guardar(turno);
        }
    }

    @TestConfiguration
    static class FailingRepositoryConfig {
        @Bean
        @Primary
        FailingOnNthSave failingRepository(JpaTurnoRepository real) {
            return new FailingOnNthSave(real);
        }
    }

    @Autowired
    private FailingOnNthSave failing;

    @Autowired
    private DemoSeedRunner runner;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @BeforeEach
    void emptyDay() {
        failing.failAt(0);
        jdbc.update("delete from turno");
    }

    private int tickets() {
        return jdbc.queryForObject("select count(*) from turno", Integer.class);
    }

    @Test
    void aFailureMidwayLeavesNoSeedRowsAndTheNextRunSeedsEverything() {
        failing.failAt(4);

        assertThatThrownBy(() -> runner.run(null)).isInstanceOf(NumeroDuplicadoException.class);

        assertThat(tickets()).isZero();

        failing.failAt(0);
        runner.run(null);

        assertThat(tickets()).isEqualTo(11);
        assertThat(failing.maxNumero(LocalDate.now(clock))).isEqualTo(11);
    }

    @Test
    void aFailureOnTheLastRowAlsoRollsBackEarlierRows() {
        failing.failAt(11);

        assertThatThrownBy(() -> runner.run(null)).isInstanceOf(NumeroDuplicadoException.class);

        assertThat(tickets()).isZero();
    }
}
