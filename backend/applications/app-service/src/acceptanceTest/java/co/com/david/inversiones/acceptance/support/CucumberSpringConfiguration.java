package co.com.david.inversiones.acceptance.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Wires the acceptance suite's single Spring context: the real application, booted with
 * {@code @SpringBootTest(webEnvironment = RANDOM_PORT)} so every step definition talks to it over
 * an actual socket, a Testcontainers PostgreSQL instance in place of a real database, and a
 * WireMock server standing in for n8n.
 *
 * <p>{@link PostgreSQLContainer} and {@link WireMockServer} are started eagerly in a static
 * initializer rather than through the {@code @Testcontainers} JUnit 5 extension: this class is
 * never run as a JUnit Jupiter test class (Cucumber only inspects it for
 * {@link CucumberContextConfiguration} and {@link SpringBootTest} via Spring's
 * {@code TestContextManager}), so {@code @Container}'s lifecycle callbacks would never fire. Static
 * start is the documented approach for combining Testcontainers with Cucumber. Both resources are
 * shared by the whole suite; Testcontainers' own Ryuk reaper tears them down when the JVM exits, so
 * no explicit {@code stop()} is needed.
 *
 * <p>The signal-snapshot refresh cron is tightened from its production schedule (once a day, at
 * 10:15 America/Bogota) to once every two seconds: {@code GetSignalsDashboardUseCase} reads from
 * the persisted {@code signal_snapshots} table, not live from n8n, and the only way to populate that
 * table is the real {@code RefreshSignalSnapshotUseCase} running on its schedule — step definitions
 * must not invoke it directly, so the schedule itself is sped up for the test run instead, and step
 * definitions poll the HTTP API until the scheduled run has caught up with whatever WireMock is
 * currently stubbed.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
public class CucumberSpringConfiguration {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    static {
        POSTGRES.start();
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("inversiones.n8n.base-url", WIREMOCK::baseUrl);
        registry.add("inversiones.signal-snapshot.refresh-cron", () -> "*/2 * * * * *");
        registry.add("inversiones.signal-snapshot.refresh-zone", () -> "UTC");
    }

    /**
     * Exposes the shared WireMock instance as a bean so support classes can be autowired with it.
     *
     * <p>A separate static nested {@link TestConfiguration}, not a {@code @Bean} method directly on
     * {@link CucumberSpringConfiguration}: Spring Boot's test support explicitly rejects
     * {@code @Bean} methods declared on the test class itself.
     */
    @TestConfiguration
    static class WireMockConfig {

        @Bean
        WireMockServer wireMockServer() {
            return WIREMOCK;
        }
    }
}
