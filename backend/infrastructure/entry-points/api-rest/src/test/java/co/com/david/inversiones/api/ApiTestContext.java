package co.com.david.inversiones.api;

import co.com.david.inversiones.api.config.CorsConfig;
import co.com.david.inversiones.api.config.OpenApiConfig;
import co.com.david.inversiones.api.config.SecurityConfig;
import co.com.david.inversiones.api.error.ApiExceptionHandler;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Base application context shared by every controller test: the real
 * {@link ApiExceptionHandler}, real {@link SecurityConfig} and {@link CorsConfig}, and a
 * test-only {@link JwtEncoder}/{@link JwtDecoder} pair mirroring {@code SecurityFilterChainIT}'s
 * (this module does not depend on {@code :security-jwt}; the filter chain only needs a
 * {@code JwtDecoder} bean, wherever it comes from).
 *
 * <p>Deliberately does <em>not</em> component-scan {@code co.com.david.inversiones.api}: doing so
 * would pull in every controller at once, forcing every test to mock every use case in the API
 * rather than just the ones its own controller needs. Each test instead adds only the controller
 * class under test to {@code @SpringBootTest(classes = {ApiTestContext.class, SomeController.class})}
 * and supplies that controller's use cases with {@code @MockitoBean} — {@code :api-rest} depends
 * only on {@code :usecase} and {@code :model}, never on a concrete gateway adapter, so a use case
 * bean does not otherwise exist in this module's test context.
 */
@Configuration
@EnableAutoConfiguration
@Import({SecurityConfig.class, CorsConfig.class, OpenApiConfig.class, ApiExceptionHandler.class})
public class ApiTestContext {

    public static final String TEST_SECRET = "it-test-secret-key-at-least-32-bytes-long!!";

    @Bean
    JwtEncoder jwtEncoder() {
        var secretKey = new SecretKeySpec(TEST_SECRET.getBytes(UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        var secretKey = new SecretKeySpec(TEST_SECRET.getBytes(UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** Issues a short-lived token for {@code subject}, signed with the test secret above. */
    public static String issueToken(JwtEncoder encoder, String subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
