package com.systemturn.turnos.infrastructure;

import com.systemturn.turnos.application.TurnoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

@Configuration
public class TurnosConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    InMemoryTurnoRepository turnoRepository(@Value("${turnos.seed-file}") Resource seed) {
        InMemoryTurnoRepository repository = new InMemoryTurnoRepository();
        CsvSeedLoader.load(seed, repository);
        return repository;
    }

    @Bean
    TurnoService turnoService(InMemoryTurnoRepository repository, Clock clock) {
        return new TurnoService(repository, clock);
    }

    @Bean
    WebMvcConfigurer cors(@Value("${turnos.allowed-origins}") String[] origins) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**").allowedOrigins(origins).allowedMethods("GET", "POST", "PATCH");
            }
        };
    }
}
