package com.systemturn.turnos.infrastructure;

import com.systemturn.turnos.application.TurnoService;
import com.systemturn.turnos.domain.EstadoTurno;
import com.systemturn.turnos.domain.Turno;
import com.systemturn.turnos.domain.TurnoRepository;
import com.systemturn.turnos.testsupport.InMemoryTurnoRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoSeedRunnerTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDate DIA_ANTERIOR = LocalDate.of(2026, 10, 29);
    private static final LocalDate HOY = LocalDate.of(2026, 10, 30);

    private final Resource csv = new ClassPathResource("turnos_hoy.csv");
    private final InMemoryTurnoRepository repository = new InMemoryTurnoRepository();

    private static Clock relojDel(LocalDate dia) {
        return Clock.fixed(dia.atTime(9, 0).atZone(BOGOTA).toInstant(), BOGOTA);
    }

    private DemoSeedRunner runner(LocalDate dia) {
        return new DemoSeedRunner(repository, csv, relojDel(dia));
    }

    @Test
    void demoStartupCreatesElevenTicketsDatedToday() throws Exception {
        runner(HOY).run(null);

        List<Turno> fila = repository.findByFecha(HOY);
        assertThat(fila).hasSize(11).allSatisfy(t -> assertThat(t.getFecha()).isEqualTo(HOY));
        assertThat(fila).extracting(Turno::getNumero).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        assertThat(fila).extracting(Turno::getEstado).containsExactly(
                EstadoTurno.ATENDIDO, EstadoTurno.ATENDIDO, EstadoTurno.ATENDIDO, EstadoTurno.ATENDIDO,
                EstadoTurno.ATENDIDO, EstadoTurno.PENDIENTE, EstadoTurno.CANCELADO, EstadoTurno.PENDIENTE,
                EstadoTurno.PENDIENTE, EstadoTurno.PENDIENTE, EstadoTurno.PENDIENTE);
    }

    @Test
    void theNextRequestedTicketAfterTheSeedIsT012() throws Exception {
        runner(HOY).run(null);

        TurnoService service = new TurnoService(repository, relojDel(HOY), 10);

        assertThat(service.solicitar().codigo()).isEqualTo("T-012");
    }

    @Test
    void secondStartupOnTheSameDayDoesNotDuplicate() throws Exception {
        runner(HOY).run(null);
        runner(HOY).run(null);

        assertThat(repository.findByFecha(HOY)).hasSize(11);
    }

    @Test
    void secondStartupKeepsExistingProgressUntouched() throws Exception {
        repository.guardar(Turno.nuevo(HOY, 1, LocalTime.of(8, 0)));

        runner(HOY).run(null);

        assertThat(repository.findByFecha(HOY)).hasSize(1);
    }

    @Test
    void newDaySeedsAgainEvenWhenEarlierDaysHaveTickets() throws Exception {
        runner(DIA_ANTERIOR).run(null);

        runner(HOY).run(null);

        assertThat(repository.findByFecha(DIA_ANTERIOR)).hasSize(11);
        assertThat(repository.findByFecha(HOY)).hasSize(11)
                .allSatisfy(t -> assertThat(t.getFecha()).isEqualTo(HOY));
    }

    @Test
    void idsFollowArrivalOrderEvenWhenCsvRowsAreOutOfOrder() throws Exception {
        String desordenado = """
                numero,hora_solicitud,hora_atencion,estado
                3,8:30,,pendiente
                1,8:02,8:05,atendido
                2,8:10,,pendiente
                """;
        Resource recurso = new ByteArrayResource(desordenado.getBytes(StandardCharsets.UTF_8));

        new DemoSeedRunner(repository, recurso, relojDel(HOY)).run(null);

        List<Turno> porId = repository.findByFecha(HOY).stream()
                .sorted(java.util.Comparator.comparing(Turno::getId)).toList();
        assertThat(porId).extracting(Turno::getNumero).containsExactly(1, 2, 3);
        assertThat(porId).extracting(Turno::getHoraSolicitud)
                .containsExactly(LocalTime.of(8, 2), LocalTime.of(8, 10), LocalTime.of(8, 30));
    }

    @Test
    void parserReturnsTicketsDatedAndOrderedByRequestTimeWithoutPersisting() {
        List<Turno> parseados = CsvSeedLoader.parse(csv, HOY);

        assertThat(parseados).hasSize(11).allSatisfy(t -> {
            assertThat(t.getFecha()).isEqualTo(HOY);
            assertThat(t.getId()).isNull();
        });
        assertThat(parseados).extracting(Turno::getNumero).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        assertThat(repository.findByFecha(HOY)).isEmpty();
    }

    /**
     * Wiring: the seed bean exists only under the {@code demo} profile. The {@code turnos.test-context} property
     * only gives each nested class its own cached context (and so its own in-memory database).
     */
    @Nested
    @SpringBootTest(properties = "turnos.test-context=demo-seed-demo")
    @ActiveProfiles({"dev", "demo", "test"})
    class DemoProfile {

        @Autowired
        private TurnoRepository turnos;

        @Autowired
        private Clock clock;

        @Autowired
        private ApplicationContext contexto;

        @Test
        void startsWithTheSeededQueueForToday() {
            assertThat(contexto.getBeansOfType(DemoSeedRunner.class)).hasSize(1);
            assertThat(turnos.findByFecha(LocalDate.now(clock))).hasSize(11);
            assertThat(turnos.maxNumero(LocalDate.now(clock))).isEqualTo(11);
        }
    }

    @Nested
    @SpringBootTest(properties = "turnos.test-context=demo-seed-default")
    @ActiveProfiles("test")
    class DefaultProfile {

        @Autowired
        private TurnoRepository turnos;

        @Autowired
        private Clock clock;

        @Autowired
        private ApplicationContext contexto;

        @Test
        void startsWithAnEmptyQueue() {
            assertThat(contexto.getBeansOfType(DemoSeedRunner.class)).isEmpty();
            assertThat(turnos.findByFecha(LocalDate.now(clock))).isEmpty();
            assertThat(turnos.maxNumero(LocalDate.now(clock))).isZero();
        }
    }
}
