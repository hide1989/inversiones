package co.com.david.inversiones.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * The exact set of origins allowed to call this API cross-origin.
 *
 * <p>The scaffold left CORS as a manually registered {@code FilterRegistrationBean<CorsFilter>},
 * independent of any authentication decision. That class is replaced (not extended) here: with
 * Spring Security now in front of every request, this class exposes only a
 * {@link CorsConfigurationSource} bean, which {@code SecurityConfig} wires into
 * {@code HttpSecurity#cors}. Registering a second, separate {@code CorsFilter} alongside Spring
 * Security's own CORS handling would be two competing mechanisms guarding the same decision.
 *
 * <p>The allowed origins are read from {@code inversiones.security.allowed-origins}, with no
 * wildcard: an explicit list, defaulting to the Vite dev server Plan 3 runs
 * ({@code http://localhost:5173}).
 */
@Configuration
public class CorsConfig {

    /**
     * {@code @Primary} because Spring MVC's own {@code HandlerMappingIntrospector} also implements
     * {@link CorsConfigurationSource}; without it, {@code SecurityConfig}'s injection of this bean
     * is ambiguous between the two.
     */
    @Bean
    @Primary
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${inversiones.security.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(CorsConfiguration.ALL));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
