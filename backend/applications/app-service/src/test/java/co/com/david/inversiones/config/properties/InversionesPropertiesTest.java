package co.com.david.inversiones.config.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link InversionesProperties} is bound and validated eagerly: a well-formed
 * {@code inversiones.*} tree produces a usable bean, and a malformed value fails context refresh
 * with {@link BindValidationException} naming the offending property, rather than producing a null
 * or a wrong value that only breaks at first use.
 */
class InversionesPropertiesTest {

    private static final String[] VALID_PROPERTIES = {
            "inversiones.base-trm=3568.42",
            "inversiones.n8n.base-url=https://n8n.example.com/webhook/inv",
            "inversiones.n8n.connect-timeout=5s",
            "inversiones.n8n.read-timeout=30s",
            "inversiones.n8n.slow-read-timeout=90s",
            "inversiones.security.jwt-secret=local-development-secret-change-me-32bytes",
            "inversiones.security.jwt-expiration-minutes=480",
            "inversiones.security.allowed-origins=http://localhost:5173",
            "inversiones.signal-snapshot.refresh-cron=0 15 10 * * MON-FRI",
            "inversiones.signal-snapshot.refresh-zone=America/Bogota"
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnableInversionesProperties.class);

    @Test
    void bindsTheFullTreeWhenEveryValueIsWellFormed() {
        runner.withPropertyValues(VALID_PROPERTIES).run(context -> {
            assertThat(context).hasNotFailed();
            InversionesProperties properties = context.getBean(InversionesProperties.class);
            assertThat(properties.baseTrm().doubleValue()).isEqualTo(3568.42);
            assertThat(properties.n8n().baseUrl()).isEqualTo("https://n8n.example.com/webhook/inv");
            assertThat(properties.security().allowedOrigins()).containsExactly("http://localhost:5173");
            assertThat(properties.signalSnapshot().refreshCron()).isEqualTo("0 15 10 * * MON-FRI");
        });
    }

    @Test
    void rejectsANonPositiveBaseTrm() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("inversiones.base-trm=-1")
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("baseTrm"));
    }

    @Test
    void rejectsAJwtSecretShorterThanThirtyTwoBytes() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("inversiones.security.jwt-secret=too-short")
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("jwtSecret"));
    }

    @Test
    void rejectsAnEmptyAllowedOriginsList() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("inversiones.security.allowed-origins=")
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("allowedOrigins"));
    }

    @Test
    void rejectsABlankN8nBaseUrl() {
        runner.withPropertyValues(VALID_PROPERTIES)
                .withPropertyValues("inversiones.n8n.base-url=")
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("baseUrl"));
    }

    @Configuration
    @EnableConfigurationProperties(InversionesProperties.class)
    static class EnableInversionesProperties {
    }
}
