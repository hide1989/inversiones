package co.com.david.inversiones.security;

import co.com.david.inversiones.model.AuthenticatedSession;
import co.com.david.inversiones.model.User;
import co.com.david.inversiones.model.gateways.TimeProvider;
import co.com.david.inversiones.security.support.MutableClock;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderAdapterTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";
    private static final long EXPIRATION_MINUTES = 15L;

    private MutableClock clock;
    private TimeProvider timeProvider;
    private JwtEncoder jwtEncoder;
    private NimbusJwtDecoder jwtDecoder;
    private JwtTokenProviderAdapter tokenProvider;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        timeProvider = new TimeProvider() {
            @Override
            public LocalDate today() {
                return LocalDate.now(clock);
            }

            @Override
            public Instant now() {
                return clock.instant();
            }
        };

        var secretKey = new SecretKeySpec(SECRET.getBytes(UTF_8), "HmacSHA256");
        jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setClock(clock);
        jwtDecoder.setJwtValidator(timestampValidator);

        tokenProvider = new JwtTokenProviderAdapter(jwtEncoder, timeProvider, EXPIRATION_MINUTES);
    }

    private User user() {
        return User.builder().id(1L).username("david").passwordHash("irrelevant-hash").build();
    }

    @Test
    void issuedTokenValidatesAndCarriesTheExpectedSubject() {
        AuthenticatedSession session = tokenProvider.issue(user());

        Jwt jwt = jwtDecoder.decode(session.token());

        assertThat(jwt.getSubject()).isEqualTo("david");
        assertThat(session.username()).isEqualTo("david");
        assertThat(session.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(EXPIRATION_MINUTES)));
    }

    @Test
    void tokenPastItsExpiryIsRejectedAfterAdvancingTheInjectedClock() {
        AuthenticatedSession session = tokenProvider.issue(user());

        // JwtTimestampValidator tolerates a default 60s clock skew around the exact expiry
        // instant, so advance well past both the expiration window and that skew.
        clock.advance(Duration.ofMinutes(EXPIRATION_MINUTES).plusMinutes(5));

        assertThatThrownBy(() -> jwtDecoder.decode(session.token()))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        AuthenticatedSession session = tokenProvider.issue(user());

        var otherSecretKey = new SecretKeySpec(
                "a-completely-different-secret-key-32-bytes-plus".getBytes(UTF_8), "HmacSHA256");
        JwtDecoder decoderWithOtherSecret =
                NimbusJwtDecoder.withSecretKey(otherSecretKey).macAlgorithm(MacAlgorithm.HS256).build();

        assertThatThrownBy(() -> decoderWithOtherSecret.decode(session.token()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenWithATamperedPayloadIsRejected() {
        AuthenticatedSession session = tokenProvider.issue(user());

        String[] parts = session.token().split("\\.");
        // Flip the payload segment to another valid-looking Base64Url string of the same shape
        // (reversing it), keeping the header and signature untouched: the signature can no longer
        // match the (different) payload it is checked against.
        String tamperedPayload = new StringBuilder(parts[1]).reverse().toString();
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtDecoder.decode(tamperedToken))
                .isInstanceOf(JwtException.class);
    }
}
