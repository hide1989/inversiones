package co.com.david.inversiones.api.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private CorsConfig corsConfig;

    @BeforeEach
    void setUp() {
        corsConfig = new CorsConfig();
    }

    @Test
    void exposesExactlyTheConfiguredOriginsWithNoWildcard() {
        List<String> origins = List.of("http://example.com", "http://another.com");

        CorsConfigurationSource source = corsConfig.corsConfigurationSource(origins);

        assertThat(source).isInstanceOf(UrlBasedCorsConfigurationSource.class);
        CorsConfiguration config = ((UrlBasedCorsConfigurationSource) source).getCorsConfigurations().get("/**");

        assertThat(config).isNotNull();
        assertThat(config.getAllowCredentials()).isTrue();
        assertThat(config.getAllowedOrigins()).containsExactlyElementsOf(origins);
        assertThat(config.getAllowedOrigins()).doesNotContain(CorsConfiguration.ALL);
        assertThat(config.getAllowedMethods())
                .containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(config.getAllowedHeaders()).containsExactly(CorsConfiguration.ALL);
    }
}
