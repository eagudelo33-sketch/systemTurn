package com.systemturn.turnos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP against a booted application (random port): the probe groups ignore the database and only {@code health}
 * is exposed. The "database down" boot points at a PostgreSQL URL on a port nobody listens on (reserved then released
 * at run time, same technique as {@code DatabaseDownFailFastTest}); nothing sleeps.
 */
class HealthGroupsTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private record Respuesta(int status, String cuerpo) { }

    private static Respuesta get(ConfigurableApplicationContext ctx, String ruta) throws Exception {
        String puerto = ctx.getEnvironment().getProperty("local.server.port");
        HttpResponse<String> r = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return new Respuesta(r.statusCode(), r.body());
    }

    private static ConfigurableApplicationContext arrancarConH2() {
        return new SpringApplicationBuilder(TurnosApiApplication.class)
                .web(WebApplicationType.SERVLET)
                .run("--spring.profiles.active=test", "--server.port=0");
    }

    private static ConfigurableApplicationContext arrancarConBaseCaida() throws IOException {
        int puerto;
        try (ServerSocket socket = new ServerSocket(0)) {
            puerto = socket.getLocalPort();
        }
        return new SpringApplicationBuilder(TurnosApiApplication.class)
                .web(WebApplicationType.SERVLET)
                .run("--server.port=0",
                        "--spring.datasource.url=jdbc:postgresql://127.0.0.1:" + puerto + "/turnos",
                        "--spring.datasource.username=turnos",
                        "--spring.datasource.password=turnos",
                        "--spring.flyway.enabled=false",
                        "--spring.jpa.hibernate.ddl-auto=none",
                        "--spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                        "--spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
                        "--spring.datasource.hikari.initialization-fail-timeout=-1");
    }

    @Test
    void configurationExposesOnlyHealthWithProbeGroupsThatExcludeTheDatabase() {
        // Scenarios: Only health is exposed / Probes UP (group contents are explicit, never defaults)
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    Binder binder = Binder.get(context.getEnvironment());
                    assertThat(binder.bind("management.endpoints.web.exposure.include", String[].class).get())
                            .containsExactly("health");
                    assertThat(binder.bind("management.endpoint.health.probes.enabled", Boolean.class).get()).isTrue();
                    assertThat(binder.bind("management.endpoint.health.show-details", String.class).get()).isEqualTo("never");
                    assertThat(binder.bind("management.endpoint.health.group.readiness.include", String[].class).get())
                            .containsExactly("readinessState");
                    assertThat(binder.bind("management.endpoint.health.group.liveness.include", String[].class).get())
                            .containsExactly("livenessState");
                });
    }

    @Test
    void probesUpWithAHealthyDatabase() throws Exception {
        // Scenario: Probes UP with a healthy database
        try (ConfigurableApplicationContext ctx = arrancarConH2()) {
            Respuesta readiness = get(ctx, "/actuator/health/readiness");
            Respuesta liveness = get(ctx, "/actuator/health/liveness");

            assertThat(readiness.status()).isEqualTo(200);
            assertThat(readiness.cuerpo()).contains("\"status\":\"UP\"");
            assertThat(liveness.status()).isEqualTo(200);
            assertThat(liveness.cuerpo()).contains("\"status\":\"UP\"");
        }
    }

    @Test
    void probesStayUpWithTheDatabaseDown() throws Exception {
        // Scenario: Probes stay UP with the database down
        try (ConfigurableApplicationContext ctx = arrancarConBaseCaida()) {
            // Proof the database really is down: the aggregate health, which does include it, is not UP.
            assertThat(get(ctx, "/actuator/health").status()).isEqualTo(503);

            Respuesta readiness = get(ctx, "/actuator/health/readiness");
            Respuesta liveness = get(ctx, "/actuator/health/liveness");

            assertThat(readiness.status()).isEqualTo(200);
            assertThat(readiness.cuerpo()).contains("\"status\":\"UP\"").doesNotContain("db");
            assertThat(liveness.status()).isEqualTo(200);
            assertThat(liveness.cuerpo()).contains("\"status\":\"UP\"").doesNotContain("db");
        }
    }

    @Test
    void onlyHealthIsExposedAndWithoutDetails() throws Exception {
        // Scenario: Only health is exposed
        try (ConfigurableApplicationContext ctx = arrancarConH2()) {
            assertThat(get(ctx, "/actuator/env").status()).isEqualTo(404);
            assertThat(get(ctx, "/actuator/beans").status()).isEqualTo(404);
            assertThat(get(ctx, "/actuator/info").status()).isEqualTo(404);

            Respuesta salud = get(ctx, "/actuator/health");
            assertThat(salud.status()).isEqualTo(200);
            assertThat(salud.cuerpo()).doesNotContain("components").doesNotContain("jdbc").doesNotContain("H2");
        }
    }
}
