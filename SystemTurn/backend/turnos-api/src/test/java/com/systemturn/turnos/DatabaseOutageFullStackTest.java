package com.systemturn.turnos;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario "Full-stack outage returns 503 without Docker": the real application (controller, service, JPA adapter and
 * Hikari pool) boots on a random port against a PostgreSQL URL on a port nobody listens on, and real HTTP requests must
 * get the 503 contract. No container is involved, so this runs under plain {@code ./mvnw test}.
 */
class DatabaseOutageFullStackTest {

    private static final long CONNECTION_TIMEOUT_MS = 1000;
    private static final Duration FAST_FAILURE = Duration.ofMillis(CONNECTION_TIMEOUT_MS + 2000);

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static ConfigurableApplicationContext ctx;
    private static int puertoCaido;

    private record Respuesta(int status, String cuerpo, Duration duracion) { }

    @BeforeAll
    static void arrancarConBaseCaida() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            puertoCaido = socket.getLocalPort();
        }
        ctx = new SpringApplicationBuilder(TurnosApiApplication.class)
                .web(WebApplicationType.SERVLET)
                .run("--server.port=0",
                        "--spring.datasource.url=jdbc:postgresql://127.0.0.1:" + puertoCaido + "/turnos",
                        "--spring.datasource.username=turnos",
                        "--spring.datasource.password=turnos",
                        "--spring.datasource.hikari.connection-timeout=" + CONNECTION_TIMEOUT_MS,
                        "--spring.datasource.hikari.initialization-fail-timeout=-1",
                        "--spring.flyway.enabled=false",
                        "--spring.jpa.hibernate.ddl-auto=none",
                        "--spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                        "--spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false");
    }

    @AfterAll
    static void cerrar() {
        if (ctx != null) {
            ctx.close();
        }
    }

    private static Respuesta enviar(HttpRequest.Builder request, String ruta) throws Exception {
        String puerto = ctx.getEnvironment().getProperty("local.server.port");
        long inicio = System.nanoTime();
        HttpResponse<String> r = HTTP.send(
                request.uri(URI.create("http://127.0.0.1:" + puerto + ruta)).build(),
                HttpResponse.BodyHandlers.ofString());
        return new Respuesta(r.statusCode(), r.body(), Duration.ofNanos(System.nanoTime() - inicio));
    }

    private static void assertServicioNoDisponible(Respuesta r) {
        assertThat(r.status()).isEqualTo(503);
        assertThat(r.cuerpo())
                .contains("\"error\":\"SERVICIO_NO_DISPONIBLE\"")
                .containsPattern("\"mensaje\":\"[^\"]+\"")
                .doesNotContainIgnoringCase("jdbc")
                .doesNotContainIgnoringCase("postgresql")
                .doesNotContain("127.0.0.1")
                .doesNotContain(String.valueOf(puertoCaido));
        assertThat(r.duracion()).isLessThan(FAST_FAILURE);
    }

    @Test
    void solicitarTurnoReturns503WithTheDatabaseDown() throws Exception {
        Respuesta r = enviar(HttpRequest.newBuilder().POST(HttpRequest.BodyPublishers.noBody()), "/api/v1/turnos");

        assertServicioNoDisponible(r);
    }

    @Test
    void dashboardReturns503WithTheDatabaseDown() throws Exception {
        Respuesta r = enviar(HttpRequest.newBuilder().GET(), "/api/v1/turnos/dashboard");

        assertServicioNoDisponible(r);
    }
}
