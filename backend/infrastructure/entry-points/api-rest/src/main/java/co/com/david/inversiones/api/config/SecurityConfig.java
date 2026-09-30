package co.com.david.inversiones.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * The API's filter chain: stateless, bearer-token authenticated, default-deny.
 *
 * <p>Authentication itself (parsing and validating the JWT) is delegated to Spring Security's
 * OAuth2 resource-server support, wired against the {@code JwtDecoder} bean from
 * {@code :security-jwt} — this class only declares which paths need a token and how a missing or
 * invalid one is reported.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_GET_PATHS = {
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // No session cookie exists to protect: every request authenticates itself with a
                // bearer token, so CSRF — which defends session-cookie-based clients against forged
                // form submissions — has nothing to do here.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemDetailEntryPoint()));
        return http.build();
    }

    /**
     * Rejects an unauthenticated request with a 401 RFC 7807 {@code ProblemDetail}, never a stack
     * trace or HTML, and never a hint about which half of the credentials was wrong — the same
     * message regardless of whether a token is missing, malformed, expired, or signed with the
     * wrong key, so this layer cannot undo the constant-response guarantee
     * {@code AuthenticateUserUseCase} already provides at the domain level.
     */
    private AuthenticationEntryPoint problemDetailEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"about:blank","title":"Unauthorized","status":401,"detail":"Authentication required"}""");
        };
    }
}
