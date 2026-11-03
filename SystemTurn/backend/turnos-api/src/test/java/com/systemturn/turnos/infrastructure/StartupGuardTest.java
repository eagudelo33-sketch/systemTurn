package com.systemturn.turnos.infrastructure;

import com.systemturn.turnos.TurnosApiApplication;
import com.systemturn.turnos.application.TurnoService;
import com.systemturn.turnos.domain.TurnoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure rules of the startup guard first, then real boots proving it fires before any database is touched. */
class StartupGuardTest {

    private static final String H2_MEM = "jdbc:h2:mem:x";
    private static final String H2_FILE = "jdbc:h2:file:./data/turnos";
    private static final String POSTGRES = "jdbc:postgresql://db:5432/turnos";

    private static String path(Path directory, String name) {
        return directory.resolve(name).toAbsolutePath().toString().replace('\\', '/');
    }

    // --- pure rules: datasource ---

    @Test
    void h2WithoutDevOrTestIsRejectedWithAMessageNamingTheFix() {
        Optional<String> violation = StartupGuard.datasourceViolation(H2_MEM, List.of());

        assertThat(violation).isPresent();
        assertThat(violation.get()).contains("SPRING_DATASOURCE_URL").contains("dev").contains(H2_MEM);
    }

    @Test
    void h2WithProdIsRejected() {
        assertThat(StartupGuard.datasourceViolation(H2_FILE, List.of("prod"))).isPresent();
    }

    @Test
    void h2WithDevIsAllowed() {
        assertThat(StartupGuard.datasourceViolation(H2_FILE, List.of("dev"))).isEmpty();
    }

    @Test
    void h2WithTestIsAllowed() {
        assertThat(StartupGuard.datasourceViolation(H2_MEM, List.of("test"))).isEmpty();
    }

    @Test
    void h2WithProdAndTestIsStillAllowedBecauseTestIsAnExplicitOptIn() {
        assertThat(StartupGuard.datasourceViolation(H2_MEM, List.of("prod", "test"))).isEmpty();
    }

    @Test
    void postgresqlPassesWithoutDevOrTest() {
        assertThat(StartupGuard.datasourceViolation(POSTGRES, List.of())).isEmpty();
        assertThat(StartupGuard.datasourceViolation(POSTGRES, List.of("prod"))).isEmpty();
    }

    @Test
    void anEmbeddedFallbackWithoutUrlIsRejectedAndTheMessageNamesTheMissingProperty() {
        Optional<String> violation = StartupGuard.datasourceViolation(null, List.of());

        assertThat(violation).isPresent();
        assertThat(violation.get()).contains("spring.datasource.url").contains("SPRING_DATASOURCE_URL").contains("dev");
        assertThat(StartupGuard.datasourceViolation(null, List.of("dev"))).isEmpty();
    }

    // --- pure rules: demo ---

    @Test
    void demoAloneIsRejected() {
        Optional<String> violation = StartupGuard.demoViolation(List.of("demo"));

        assertThat(violation).isPresent();
        assertThat(violation.get()).contains("demo").contains("dev");
    }

    @Test
    void demoWithProdIsRejected() {
        assertThat(StartupGuard.demoViolation(List.of("prod", "demo"))).isPresent();
    }

    @Test
    void demoWithTestButWithoutDevIsRejected() {
        assertThat(StartupGuard.demoViolation(List.of("test", "demo"))).isPresent();
    }

    @Test
    void demoWithDevIsAllowed() {
        assertThat(StartupGuard.demoViolation(List.of("dev", "demo"))).isEmpty();
    }

    @Test
    void withoutDemoThereIsNothingToCheck() {
        assertThat(StartupGuard.demoViolation(List.of())).isEmpty();
        assertThat(StartupGuard.demoViolation(List.of("prod"))).isEmpty();
    }

    // --- boots ---

    @Test
    void missingUrlFailsStartupNamingThePropertyAndNeverUsesAnEmbeddedDatabase() {
        assertThatThrownBy(() -> boot())
                .hasStackTraceContaining("spring.datasource.url")
                .hasStackTraceContaining("SPRING_DATASOURCE_URL");
    }

    @Test
    void anExplicitH2UrlWithoutDevOrTestFailsStartup() {
        assertThatThrownBy(() -> boot("--spring.profiles.active=prod", "--spring.datasource.url=" + H2_MEM))
                .hasStackTraceContaining(H2_MEM)
                .hasStackTraceContaining("dev");
    }

    @Test
    void demoWithoutDevFailsStartupEvenWithATestDatabase() {
        assertThatThrownBy(() -> boot("--spring.profiles.active=test,demo"))
                .hasStackTraceContaining("demo")
                .hasStackTraceContaining("dev");
    }

    @Test
    void demoWithDevIsAcceptedAndSeedsElevenTicketsForToday(@TempDir Path directory) {
        try (ConfigurableApplicationContext context = boot("--spring.profiles.active=dev,demo",
                "--TURNOS_DB_PATH=" + path(directory, "turnos"))) {
            LocalDate hoy = LocalDate.now(context.getBean(Clock.class));
            assertThat(context.getBean(TurnoRepository.class).findByFecha(hoy)).hasSize(11);
            assertThat(context.getBean(TurnoService.class).fila()).isNotEmpty();
        }
    }

    /**
     * Design open question: with profiles dev,demo,test the later profile file wins, so the in-memory URL of
     * application-test.yml overrides the H2 file URL of application-dev.yml and nothing is written to disk.
     */
    @Test
    void testProfileInMemoryUrlWinsOverTheDevFileUrl(@TempDir Path directory) throws Exception {
        Path devFile = directory.resolve("turnos");
        Path defaultData = Path.of("data");
        boolean dataExistedBefore = Files.exists(defaultData);

        try (ConfigurableApplicationContext context = boot("--spring.profiles.active=dev,demo,test",
                "--TURNOS_DB_PATH=" + path(directory, "turnos"))) {
            String url = context.getEnvironment().getProperty("spring.datasource.url");
            assertThat(url).startsWith("jdbc:h2:mem:turnos-");
            try (var connection = context.getBean(DataSource.class).getConnection()) {
                assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
            }
        }

        try (Stream<Path> created = Files.list(directory)) {
            assertThat(created.toList()).isEmpty();
        }
        assertThat(devFile.resolveSibling("turnos.mv.db")).doesNotExist();
        assertThat(Files.exists(defaultData)).isEqualTo(dataExistedBefore);
    }

    private static ConfigurableApplicationContext boot(String... args) {
        return new SpringApplicationBuilder(TurnosApiApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
