package com.systemturn.turnos;

import com.systemturn.turnos.application.TurnoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binds the real {@code application.yml} (plus profile files) against a controlled environment, so the externalised
 * datasource, pool and Flyway settings are verified without a database.
 */
class ConfigBindingTest {

    private static final Path RESOURCES = Paths.get("src", "main", "resources");
    private static final String V1_SHA256_LF = "22b697f98f57b7ea28556368ac67b39ad1a73ae00474b1b81106b2bf448d4928";
    private static final Pattern PASSWORD_LITERAL = Pattern.compile("(?i)password\\s*[:=]\\s*(\\S.*)$");

    /** Loads application.yml (and profile files) on top of an isolated, fake OS environment. */
    private static void withEnvironment(Map<String, Object> osEnvironment, String profile, Consumer<Binder> assertions) {
        new ApplicationContextRunner()
                .withInitializer(context -> {
                    ConfigurableEnvironment environment = context.getEnvironment();
                    environment.getPropertySources().remove("systemEnvironment");
                    environment.getPropertySources().remove("systemProperties");
                    environment.getPropertySources().addLast(new SystemEnvironmentPropertySource("systemEnvironment", osEnvironment));
                    if (profile != null) {
                        environment.setActiveProfiles(profile);
                    }
                })
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> assertions.accept(Binder.get(context.getEnvironment())));
    }

    @Test
    void environmentValuesAreApplied() {
        Map<String, Object> env = Map.of(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://db:5432/turnos",
                "SPRING_DATASOURCE_USERNAME", "app",
                "SPRING_DATASOURCE_PASSWORD", "s3cret");

        withEnvironment(env, null, binder -> {
            assertThat(binder.bind("spring.datasource.url", String.class).get()).isEqualTo("jdbc:postgresql://db:5432/turnos");
            assertThat(binder.bind("spring.datasource.username", String.class).get()).isEqualTo("app");
            assertThat(binder.bind("spring.datasource.password", String.class).get()).isEqualTo("s3cret");
        });
    }

    @Test
    void baseConfigurationHasNoDatasourceUrlDefault() {
        withEnvironment(Map.of(), null, binder ->
                assertThat(binder.bind("spring.datasource.url", String.class).isBound()).isFalse());
    }

    @Test
    void devProfileProvidesAnH2FileUrlFromTurnosDbPath() {
        withEnvironment(Map.of("TURNOS_DB_PATH", "/tmp/x/turnos"), "dev", binder ->
                assertThat(binder.bind("spring.datasource.url", String.class).get()).isEqualTo("jdbc:h2:file:/tmp/x/turnos"));
    }

    @Test
    void defaultsGivePoolOf10FastConnectionTimeoutAndFlywayRetries() {
        withEnvironment(Map.of(), null, binder -> {
            assertThat(binder.bind("spring.datasource.hikari.maximum-pool-size", Integer.class).get()).isEqualTo(10);
            assertThat(binder.bind("spring.datasource.hikari.connection-timeout", Integer.class).get()).isEqualTo(5000);
            assertThat(binder.bind("spring.flyway.connect-retries", Integer.class).get()).isEqualTo(10);
        });
    }

    @Test
    void poolSizeAndRetriesAreOverridableByEnvironment() {
        withEnvironment(Map.of("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE", "4"), null, binder ->
                assertThat(binder.bind("spring.datasource.hikari.maximum-pool-size", Integer.class).get()).isEqualTo(4));
        withEnvironment(Map.of("TURNOS_DB_POOL_SIZE", "7", "TURNOS_DB_CONNECT_RETRIES", "3"), null, binder -> {
            assertThat(binder.bind("spring.datasource.hikari.maximum-pool-size", Integer.class).get()).isEqualTo(7);
            assertThat(binder.bind("spring.flyway.connect-retries", Integer.class).get()).isEqualTo(3);
        });
    }

    @Test
    void noCommittedSecretsInMainResources() throws IOException {
        List<String> found;
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            found = files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().matches(".*\\.(yml|yaml|properties)$"))
                    .flatMap(p -> literalPasswords(readLines(p)).stream())
                    .toList();
        }
        assertThat(found).isEmpty();
        // the scanner itself must catch what it is meant to catch, otherwise the empty result above proves nothing
        assertThat(literalPasswords(List.of("spring:", "  datasource:", "    password: hunter2"))).containsExactly("hunter2");
        assertThat(literalPasswords(List.of("password=abc123"))).containsExactly("abc123");
        assertThat(literalPasswords(List.of("password: ${SPRING_DATASOURCE_PASSWORD}", "password:", "password: ''"))).isEmpty();
    }

    @Test
    void migrationV1IsUntouchedAndThereIsNoVendorFolder() throws Exception {
        Path migrations = RESOURCES.resolve("db").resolve("migration");
        String normalized = Files.readString(migrations.resolve("V1__create_turno.sql"), StandardCharsets.UTF_8).replace("\r\n", "\n");
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
        assertThat(sha).isEqualTo(V1_SHA256_LF);
        try (Stream<Path> entries = Files.list(migrations)) {
            assertThat(entries.toList()).noneMatch(Files::isDirectory);
        }
        try (Stream<Path> db = Files.list(RESOURCES.resolve("db"))) {
            assertThat(db.map(p -> p.getFileName().toString()).toList()).containsExactly("migration");
        }
    }

    @Test
    void devStartsEmptyOnAnH2FileAndAppliesV1(@TempDir Path directorio) {
        String path = directorio.resolve("turnos").toAbsolutePath().toString().replace('\\', '/');
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TurnosApiApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.profiles.active=dev", "--TURNOS_DB_PATH=" + path)) {
            assertThat(context.getBean(TurnoService.class).fila()).isEmpty();
            assertThat(context.getBean(JdbcTemplate.class)
                    .queryForObject("select count(*) from \"flyway_schema_history\" where \"version\" = '1' and \"success\"", Integer.class))
                    .isEqualTo(1);
            assertThat(context.getBean(TurnoService.class).solicitar().getNumero()).isEqualTo(1);
        }
        assertThat(directorio.resolve("turnos.mv.db")).exists();
    }

    @Test
    void emptyDatabaseOnTestProfileIsMigratedValidatedAndEmpty() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TurnosApiApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.profiles.active=test")) {
            assertThat(context.getBean(TurnoService.class).fila()).isEmpty();
            assertThat(context.getBean(JdbcTemplate.class).queryForObject("select count(*) from turno", Integer.class)).isZero();
        }
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Values of {@code password} keys that are neither empty nor a {@code ${...}} placeholder. */
    static List<String> literalPasswords(List<String> lines) {
        return lines.stream()
                .map(PASSWORD_LITERAL::matcher)
                .filter(Matcher::find)
                .map(m -> m.group(1).trim())
                .filter(v -> !v.isEmpty() && !v.equals("''") && !v.equals("\"\"") && !v.startsWith("${"))
                .toList();
    }
}
