package co.com.david.inversiones.api.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the real {@link SecurityConfig} and {@link CorsConfig} beans, against a
 * minimal test-only controller.
 *
 * <p>No controller in {@code src/main} is authenticated yet (Task 9 adds the real ones), so this
 * test declares its own protected endpoint here in test sources rather than shipping a
 * placeholder controller in production code. Likewise, the {@link JwtEncoder}/{@link JwtDecoder}
 * pair is a test-only stand-in for {@code :security-jwt}'s {@code JwtCodecConfig} — api-rest does
 * not depend on that module (the filter chain only needs a {@code JwtDecoder} bean, wherever it
 * comes from), so this test supplies one built the same way, over a fixed test secret.
 */
@SpringBootTest(classes = SecurityFilterChainIT.TestContext.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class SecurityFilterChainIT {

    private static final String PROTECTED_PATH = "/api/v1/_test/protected";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtEncoder jwtEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void protectedEndpointWithoutTokenReturnsUnauthorizedProblemDetail() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void protectedEndpointWithValidTokenIsReachable() throws Exception {
        String token = issueToken("david");

        mockMvc.perform(get(PROTECTED_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    void preflightFromTheAllowedOriginSucceeds() throws Exception {
        mockMvc.perform(options(PROTECTED_PATH)
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
    }

    @Test
    void preflightFromAnotherOriginIsRejected() throws Exception {
        mockMvc.perform(options(PROTECTED_PATH)
                        .header("Origin", "http://not-allowed.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    private String issueToken(String subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Configuration
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, CorsConfig.class})
    static class TestContext {

        private static final String TEST_SECRET = "it-test-secret-key-at-least-32-bytes-long!!";

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

        @RestController
        static class ProtectedTestController {

            @GetMapping(PROTECTED_PATH)
            public String ping() {
                return "pong";
            }
        }
    }
}
