package com.systemturn.turnos.application;

import com.systemturn.turnos.domain.Turno;
import com.systemturn.turnos.domain.TurnoRepository;
import com.systemturn.turnos.infrastructure.persistence.JpaTurnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Real service over the real JPA adapter and an H2 database: concurrency is resolved by the unique constraint. */
@SpringBootTest
@ActiveProfiles("test")
class SolicitarConcurrencyTest {

    private static final int HILOS = 10;

    @Autowired
    TurnoService service;

    @Autowired
    TurnoRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    /** The queue is scoped to today, so every test starts from an empty table (the PostgreSQL database is shared). */
    @BeforeEach
    void limpiar() {
        jdbc.update("delete from turno");
    }

    @Test
    void theServiceRunsOverTheJpaAdapter() {
        assertThat(repository).isInstanceOf(JpaTurnoRepository.class);
    }

    @Test
    void concurrentRequestsGetEachNumberFromOneToTenExactlyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        CountDownLatch listos = new CountDownLatch(HILOS);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Turno>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < HILOS; i++) {
                Callable<Turno> tarea = () -> {
                    listos.countDown();
                    salida.await();
                    return service.solicitar();
                };
                resultados.add(pool.submit(tarea));
            }
            assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue();
            salida.countDown();

            List<Integer> numeros = new ArrayList<>();
            for (Future<Turno> resultado : resultados) {
                numeros.add(resultado.get(30, TimeUnit.SECONDS).getNumero());
            }

            assertThat(numeros).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, HILOS).boxed().toList());
            assertThat(service.fila()).extracting(Turno::getNumero).hasSize(HILOS).doesNotHaveDuplicates();
        } finally {
            pool.shutdownNow();
        }
    }
}
