package com.systemturn.turnos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GracefulShutdownTest {

    private static final long LIMITE_SEGUNDOS = 20;

    @Test
    void configurationShutdownIsGracefulWithA25SecondPhaseTimeout() {
        // Scenario: Configuration
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    Binder binder = Binder.get(context.getEnvironment());
                    assertThat(binder.bind("server.shutdown", String.class).get()).isEqualTo("graceful");
                    assertThat(binder.bind("spring.lifecycle.timeout-per-shutdown-phase", Duration.class).get())
                            .isEqualTo(Duration.ofSeconds(25));
                });
    }

    @Test
    void inFlightRequestCompletesWhenTheContextIsClosed(CapturedOutput salida) throws Exception {
        // Scenario: In-flight request completes
        CountDownLatch enVuelo = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        CountDownLatch cerrando = new CountDownLatch(1);

        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(TurnosApiApplication.class)
                .web(WebApplicationType.SERVLET)
                .initializers(context -> {
                    // Test-only slow endpoint, registered programmatically so component scanning never sees it.
                    RouterFunction<ServerResponse> lenta = RouterFunctions.route()
                            .GET("/test-lenta", request -> {
                                enVuelo.countDown();
                                try {
                                    if (!liberar.await(LIMITE_SEGUNDOS, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException("never released");
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(e);
                                }
                                return ServerResponse.ok().body("terminada");
                            }).build();
                    context.getBeanFactory().registerSingleton("rutaLenta", lenta);
                    context.addApplicationListener((ApplicationListener<ContextClosedEvent>) e -> cerrando.countDown());
                })
                .run("--spring.profiles.active=test", "--server.port=0");
        int puerto = Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));

        CompletableFuture<HttpResponse<String>> respuesta = HttpClient.newHttpClient().sendAsync(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + "/test-lenta")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(enVuelo.await(LIMITE_SEGUNDOS, TimeUnit.SECONDS)).as("request reached the handler").isTrue();

        CompletableFuture<Void> cierre = CompletableFuture.runAsync(ctx::close);
        assertThat(cerrando.await(LIMITE_SEGUNDOS, TimeUnit.SECONDS)).as("close started").isTrue();
        // Deterministic gate: graceful shutdown stops accepting; wait (bounded) until the port refuses connections.
        esperarHastaQueElPuertoRechace(puerto);
        assertThat(respuesta).as("request is still pending while draining").isNotDone();
        assertThat(salida.getAll()).as("server entered graceful (not immediate) shutdown")
                .contains("Commencing graceful shutdown");

        liberar.countDown();

        HttpResponse<String> r = respuesta.get(LIMITE_SEGUNDOS, TimeUnit.SECONDS);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).isEqualTo("terminada");
        cierre.get(LIMITE_SEGUNDOS, TimeUnit.SECONDS);
    }

    private static void esperarHastaQueElPuertoRechace(int puerto) {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(LIMITE_SEGUNDOS);
        while (System.nanoTime() < limite) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", puerto), 200);
            } catch (IOException rechazado) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        }
        throw new AssertionError("the port kept accepting connections during shutdown");
    }
}
