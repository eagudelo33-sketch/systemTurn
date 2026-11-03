package com.systemturn.turnos.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One PostgreSQL container shared by every {@code *PostgresTest} class, started on first use and reaped by Ryuk when
 * the JVM exits. It is deliberately not a {@code @ServiceConnection} bean: Boot would stop the container when a
 * context closes, and the restart test needs two contexts on the same database.
 */
public final class PostgresSupport {

    /** Pinned patch release (not a floating tag), same major as the target RDS engine. */
    static final String IMAGE = "postgres:17.10";

    private static PostgreSQLContainer container;

    private PostgresSupport() {
    }

    public static synchronized PostgreSQLContainer container() {
        if (container == null) {
            PostgreSQLContainer nuevo = new PostgreSQLContainer(IMAGE);
            nuevo.start();
            container = nuevo;
        }
        return container;
    }

    /** For {@code @DynamicPropertySource}: points the datasource at the shared container. */
    public static void register(DynamicPropertyRegistry registry) {
        PostgreSQLContainer pg = container();
        registry.add("spring.datasource.url", pg::getJdbcUrl);
        registry.add("spring.datasource.username", pg::getUsername);
        registry.add("spring.datasource.password", pg::getPassword);
    }

    /** Command-line form of {@link #register}, for tests that boot contexts by hand. */
    public static String[] arguments() {
        PostgreSQLContainer pg = container();
        return new String[] {
                "--spring.datasource.url=" + pg.getJdbcUrl(),
                "--spring.datasource.username=" + pg.getUsername(),
                "--spring.datasource.password=" + pg.getPassword()
        };
    }

    /** Empties the ticket table (a no-op before Flyway has created it) so tests never see each other's rows. */
    public static void cleanTickets() {
        PostgreSQLContainer pg = container();
        try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             Statement s = c.createStatement()) {
            s.execute("do $$ begin if to_regclass('public.turno') is not null then delete from turno; end if; end $$");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not clean the PostgreSQL test database", e);
        }
    }
}
