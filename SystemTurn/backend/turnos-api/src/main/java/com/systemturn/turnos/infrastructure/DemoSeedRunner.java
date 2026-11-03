package com.systemturn.turnos.infrastructure;

import com.systemturn.turnos.domain.TurnoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Loads the sample queue, dated today, only under the {@code demo} profile. Idempotent per day: it is skipped
 * when today already has tickets, so a restart over a persisted database never duplicates them. The whole seed
 * runs in one transaction: if any row fails, none remain and the next startup seeds again.
 */
@Component
@Profile("demo")
public class DemoSeedRunner implements ApplicationRunner {

    private final TurnoRepository repository;
    private final Resource seed;
    private final Clock clock;

    public DemoSeedRunner(TurnoRepository repository, @Value("${turnos.seed-file}") Resource seed, Clock clock) {
        this.repository = repository;
        this.seed = seed;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDate hoy = LocalDate.now(clock);
        if (repository.maxNumero(hoy) > 0) {
            return;
        }
        CsvSeedLoader.parse(seed, hoy).forEach(repository::guardar);
    }
}
