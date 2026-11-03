package com.systemturn.turnos.infrastructure;

import com.systemturn.turnos.application.TurnoService;
import com.systemturn.turnos.domain.TurnoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TurnosConfig {

    private static final int MAX_INTENTOS = 10;

    @Bean
    Clock clock(@Value("${turnos.timezone:America/Bogota}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }

    @Bean
    TurnoService turnoService(TurnoRepository repository, Clock clock) {
        return new TurnoService(repository, clock, MAX_INTENTOS);
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
