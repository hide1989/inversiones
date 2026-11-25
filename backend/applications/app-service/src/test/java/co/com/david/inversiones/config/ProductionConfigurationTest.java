package co.com.david.inversiones.config;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The deployment configuration contract: which properties resolve, from where, and what happens
 * when a deployment forgets one.
 *
 * <p><b>Why this boots no application context.</b> These assertions are about property resolution
 * from {@code application.yml} plus the environment — nothing else. Booting a context to check them
 * would drag in the whole bean graph: a {@code @SpringBootApplication} declared in this package
 * component-scans {@code co.com.david.inversiones.config}, which holds {@link UseCasesConfig} and
 * {@link SignalSnapshotSchedulerConfig}, so the context would demand every repository and gateway
 * the application has. Excluding an autoconfiguration does not stop that scan. The test would then
 * fail because the context could not start rather than because a property was missing, which is a
 * red for the wrong reason and proves nothing about the configuration.
 *
 * <p>{@link ConfigDataEnvironmentPostProcessor#applyTo} is the supported way to load the YAML the
 * way Spring Boot would, into a bare {@link StandardEnvironment}, and stop there.
 *
 * <p><b>Why "fails to resolve" is the right assertion for a missing variable.</b> A placeholder
 * like {@code ${POSTGRES_HOST}} with no default is resolved lazily, when something reads it. At
 * startup that reader is Hikari or Flyway, and the failure surfaces as a stack trace far from the
 * cause. Asserting that the read itself throws, and that the message names the variable, pins the
 * same behaviour at the point where it is diagnosable.
 */
class ProductionConfigurationTest {

    /** Any profile that is not "local": no application-&lt;profile&gt;.yml exists for it on purpose. */
    private static final String DEPLOYED_PROFILE = "prod-test";

    private static final String THIRTY_TWO_BYTE_SECRET = "a-thirty-two-byte-minimum-secret";

    /** Loads application.yml (and any matching profile file) into a bare environment. */
    private static ConfigurableEnvironment environmentWith(Map<String, Object> variables, String... profiles) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-env", new HashMap<>(variables)));
        ConfigDataEnvironmentPostProcessor.applyTo(environment, null, null, profiles);
        return environment;
    }

    @Test
    @DisplayName("the datasource url resolves from environment variables outside the local profile")
    void datasourceResolvesFromEnvironmentOutsideLocalProfile() {
        ConfigurableEnvironment environment = environmentWith(
                Map.of("POSTGRES_HOST", "db.example.test",
                        "POSTGRES_PORT", "6000",
                        "POSTGRES_DB", "inv_prod",
                        "POSTGRES_PASSWORD", "irrelevant-here"),
                DEPLOYED_PROFILE);

        assertThat(environment.getProperty("spring.datasource.url"))
                .as("application.yml must carry the datasource, not only application-local.yml")
                .isEqualTo("jdbc:postgresql://db.example.test:6000/inv_prod");
    }

    @Test
    @DisplayName("a missing POSTGRES_HOST fails to resolve, naming the variable")
    void missingPostgresHostFailsToResolveAndNamesTheVariable() {
        ConfigurableEnvironment environment = environmentWith(
                Map.of("POSTGRES_PASSWORD", "irrelevant-here"), DEPLOYED_PROFILE);

        assertThatThrownBy(() -> environment.getProperty("spring.datasource.url"))
                .as("a deployment that forgets the host must fail, not quietly target localhost")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("POSTGRES_HOST");
    }

    @Test
    @DisplayName("a missing POSTGRES_PASSWORD fails to resolve, naming the variable")
    void missingPostgresPasswordFailsToResolveAndNamesTheVariable() {
        ConfigurableEnvironment environment = environmentWith(
                Map.of("POSTGRES_HOST", "db.example.test"), DEPLOYED_PROFILE);

        assertThatThrownBy(() -> environment.getProperty("spring.datasource.password"))
                .as("a deployment that forgets the password must fail, not use a guessable default")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("POSTGRES_PASSWORD");
    }

    @Test
    @DisplayName("a missing JWT_SECRET fails to resolve, naming the variable")
    void missingJwtSecretFailsToResolveAndNamesTheVariable() {
        ConfigurableEnvironment environment = environmentWith(Map.of(), DEPLOYED_PROFILE);

        assertThatThrownBy(() -> environment.getProperty("inversiones.security.jwt-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("a missing SECURITY_ALLOWED_ORIGINS fails to resolve, naming the variable")
    void missingAllowedOriginsFailsToResolveAndNamesTheVariable() {
        // The plan assigned this failure mode to Task 1 and Task 1 did not cover it: the base
        // application.yml carried ${SECURITY_ALLOWED_ORIGINS:http://localhost:5173}, a development
        // default visible to EVERY profile — the exact pattern the file's own jwt-secret comment
        // argues against two lines above it.
        //
        // What that costs is the hardest kind of failure to diagnose. Any path that does not go
        // through docker-compose.prod.yml — running the image by hand, a hotfix `docker run`, a
        // future host — boots with CORS allowing only http://localhost:5173. The browser then
        // reports an opaque CORS error, and the API logs nothing wrong at any level, because
        // nothing IS wrong as far as the backend knows. Failing at startup is far cheaper.
        ConfigurableEnvironment environment = environmentWith(Map.of(), DEPLOYED_PROFILE);

        assertThatThrownBy(() -> environment.getProperty("inversiones.security.allowed-origins"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SECURITY_ALLOWED_ORIGINS");
    }

    @Test
    @DisplayName("allowed origins binds a comma-separated list, so one variable carries several hosts")
    void allowedOriginsBindsACommaSeparatedList() {
        ConfigurableEnvironment environment = environmentWith(
                Map.of("SECURITY_ALLOWED_ORIGINS", "https://a.vercel.app,https://b.vercel.app"),
                DEPLOYED_PROFILE);

        assertThat(environment.getProperty("inversiones.security.allowed-origins", String[].class))
                .containsExactly("https://a.vercel.app", "https://b.vercel.app");
    }

    @Test
    @DisplayName("the local profile still supplies development defaults for every connection value")
    void localProfileStillSuppliesDevelopmentDefaults() {
        // The regression this task could cause: moving the datasource into application.yml while
        // breaking `./gradlew bootRun`, which must keep working with no environment set at all.
        ConfigurableEnvironment environment = environmentWith(Map.of(), "local");

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5432/inversiones");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("inversiones");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("inversiones_local");
        assertThat(environment.getProperty("inversiones.security.jwt-secret"))
                .isEqualTo("local-development-secret-change-me-32bytes");
    }

    @Test
    @DisplayName("the local profile's POSTGRES_PORT can be overridden, which is how 5433 reaches bootRun")
    void localProfilePortIsOverridable() {
        // This machine maps the composed Postgres to 5433 because an unrelated container holds 5432.
        // If this stopped working, `POSTGRES_PORT=5433 ./gradlew bootRun` would silently target the
        // wrong database — the exact failure that cost an afternoon before this test existed.
        ConfigurableEnvironment environment = environmentWith(Map.of("POSTGRES_PORT", "5433"), "local");

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5433/inversiones");
    }

    @Test
    @DisplayName("the 32-byte secret shape the deployment generates is accepted as a property value")
    void deploymentSecretResolvesWhenProvided() {
        ConfigurableEnvironment environment = environmentWith(
                Map.of("JWT_SECRET", THIRTY_TWO_BYTE_SECRET), DEPLOYED_PROFILE);

        assertThat(environment.getProperty("inversiones.security.jwt-secret"))
                .isEqualTo(THIRTY_TWO_BYTE_SECRET);
    }

    @Test
    @DisplayName("a missing SEED_PASSWORD_HASH fails to resolve, naming the variable")
    void missingSeedPasswordHashFailsToResolveAndNamesTheVariable() {
        // Migration V2 seeds the user `david` with the BCrypt digest of a password written in
        // plain sight in this repository, and it runs under every profile because Flyway has no
        // notion of one. Until V4 that meant a deployed instance was born with a published
        // credential, and the pipeline's smoke test depended on it still working — so rotating it
        // turned CI red, which is a guard pointing the wrong way.
        //
        // V4 rewrites the hash from a Flyway placeholder. This assertion is what makes the
        // placeholder a REQUIREMENT outside `local` rather than a nicety: with no value, Flyway
        // fails at startup naming the variable, instead of leaving the committed hash in place and
        // reporting a healthy deploy.
        ConfigurableEnvironment environment = environmentWith(Map.of(), DEPLOYED_PROFILE);

        assertThatThrownBy(() -> environment.getProperty("spring.flyway.placeholders.seed_password_hash"))
                .as("a deployment that forgets the hash must fail, not keep the committed one")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEED_PASSWORD_HASH");
    }

    @Test
    @DisplayName("the local profile supplies the committed hash, so local work needs no setup")
    void localProfileSuppliesTheCommittedSeedHash() {
        ConfigurableEnvironment environment = environmentWith(Map.of(), "local");

        assertThat(environment.getProperty("spring.flyway.placeholders.seed_password_hash"))
                .as("`./gradlew bootRun` and the dev compose stack must keep working unchanged")
                .isEqualTo("$2a$10$8h4HIXTmlqYgxHHTXt9vi.Tg1neXgrGmgZcv06pcahzg2CztxVUZ6");
    }
}
