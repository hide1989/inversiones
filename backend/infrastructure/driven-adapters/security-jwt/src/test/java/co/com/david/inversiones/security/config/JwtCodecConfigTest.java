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

    private String rootMessage(BeanCreationException ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
