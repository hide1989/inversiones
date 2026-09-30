package co.com.david.inversiones.api;

import co.com.david.inversiones.api.config.CorsConfig;
import co.com.david.inversiones.api.config.SecurityConfig;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Narrow test context for {@link ApiRestTest}: the scaffold-era {@code /api/usecase/path}
 * endpoint, with neither CORS nor the security filter chain wired in. That endpoint predates both
 * and is superseded by Task 9's real controllers; the security filter chain integration itself is
 * covered separately, against real controllers wired with {@link SecurityConfig} and
 * {@link CorsConfig} in place.
 */
@Configuration
@EnableAutoConfiguration
@EnableWebMvc
@ComponentScan(basePackages = "co.com.david.inversiones.api",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {CorsConfig.class, SecurityConfig.class}
        ))
@Import(ApiRest.class)
public class TestConfig {
}