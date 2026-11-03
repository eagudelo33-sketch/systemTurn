package com.systemturn.turnos.infrastructure;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

/**
 * Refuses to start in configurations that would silently run on a throwaway database or seed demo data into a
 * real one. It inspects the datasource that Spring Boot actually built (not only the properties), because when no
 * URL is configured Boot quietly falls back to an embedded H2. The check runs when the {@link DataSource} bean is
 * initialised, before Hikari connects and before Flyway migrates.
 */
@Configuration
public class StartupGuard {

    private static final String DEV = "dev";
    private static final String TEST = "test";
    private static final String DEMO = "demo";
    private static final String URL_PROPERTY = "spring.datasource.url";
    private static final String H2_PREFIX = "jdbc:h2:";

    @Bean
    static BeanPostProcessor datasourceGuard(Environment environment) {
        Collection<String> profiles = Arrays.asList(environment.getActiveProfiles());
        demoViolation(profiles).ifPresent(message -> {
            throw new IllegalStateException(message);
        });
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource) {
                    boolean configured = !environment.getProperty(URL_PROPERTY, "").isBlank();
                    datasourceViolation(configured ? effectiveUrl(dataSource) : null, profiles).ifPresent(message -> {
                        throw new IllegalStateException(message);
                    });
                }
                return bean;
            }
        };
    }

    /**
     * The URL the datasource will really use. A {@code null} result means "no URL was configured": the guard passes
     * {@code null} when {@code spring.datasource.url} is unset, because Boot then builds an embedded H2 behind a
     * generated URL that must not look like a deliberate choice.
     */
    private static String effectiveUrl(DataSource dataSource) {
        if (dataSource instanceof EmbeddedDatabase) {
            return null;
        }
        if (dataSource instanceof HikariDataSource hikari) {
            return hikari.getJdbcUrl();
        }
        return null;
    }

    /** An H2 or embedded datasource is only acceptable under {@code dev} or {@code test}. */
    static Optional<String> datasourceViolation(String jdbcUrl, Collection<String> activeProfiles) {
        if (activeProfiles.contains(DEV) || activeProfiles.contains(TEST)) {
            return Optional.empty();
        }
        if (jdbcUrl == null) {
            return Optional.of("No spring.datasource.url is configured, so Spring Boot fell back to an embedded "
                    + "database. Set SPRING_DATASOURCE_URL to a PostgreSQL JDBC URL, or activate the dev profile "
                    + "(--spring.profiles.active=dev) to use the local H2 file.");
        }
        if (jdbcUrl.startsWith(H2_PREFIX)) {
            return Optional.of("The datasource URL " + jdbcUrl + " is an H2 database, which is only allowed with the "
                    + "dev or test profile. Set SPRING_DATASOURCE_URL to a PostgreSQL JDBC URL, or activate the dev "
                    + "profile (--spring.profiles.active=dev).");
        }
        return Optional.empty();
    }

    /** The {@code demo} profile seeds sample tickets, so it must never run without {@code dev}. */
    static Optional<String> demoViolation(Collection<String> activeProfiles) {
        if (activeProfiles.contains(DEMO) && !activeProfiles.contains(DEV)) {
            return Optional.of("The demo profile seeds sample tickets and is only allowed together with the dev "
                    + "profile (--spring.profiles.active=dev,demo). Remove demo from the active profiles.");
        }
        return Optional.empty();
    }
}
