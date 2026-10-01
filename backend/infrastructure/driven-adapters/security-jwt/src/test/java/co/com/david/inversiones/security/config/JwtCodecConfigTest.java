package co.com.david.inversiones.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtCodecConfigTest {

    @Test
    void secretShorterThan32BytesFailsContextStartupNamingTheProperty() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Map.of("inversiones.security.jwt-secret", "too-short-secret")));
            context.register(JwtCodecConfig.class);

            assertThatThrownBy(context::refresh)
                    .isInstanceOf(BeanCreationException.class)
                    .satisfies(ex -> assertThat(rootMessage((BeanCreationException) ex))
                            .contains("inversiones.security.jwt-secret"));
        }
    }

    /**
     * The Important-4 regression: the committed local default is 40 bytes, long enough to pass the
     * length check above, so without this guard a deployment running under any profile other than
     * {@code local} that forgot to set {@code JWT_SECRET} and inherited the committed default would
     * boot and sign tokens with a key published in this repository.
     */
    @Test
    void theCommittedLocalDefaultIsRejectedOutsideTheLocalProfile() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Map.of("inversiones.security.jwt-secret", JwtCodecConfig.COMMITTED_LOCAL_DEFAULT)));
            context.register(JwtCodecConfig.class);

            assertThatThrownBy(context::refresh)
                    .isInstanceOf(BeanCreationException.class)
                    .satisfies(ex -> assertThat(rootMessage((BeanCreationException) ex))
                            .contains("committed local-development default"));
        }
    }

    /**
     * The same committed value must keep working with no manual step when the {@code local}
     * profile is the one in force — this is the local-development path the project is required to
     * keep zero-setup.
     */
    @Test
    void theCommittedLocalDefaultIsAcceptedUnderTheLocalProfile() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("local");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Map.of("inversiones.security.jwt-secret", JwtCodecConfig.COMMITTED_LOCAL_DEFAULT)));
            context.register(JwtCodecConfig.class);

            context.refresh();

            assertThat(context.getBean(JwtCodecConfig.class)).isNotNull();
        }
    }

    private String rootMessage(BeanCreationException ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
