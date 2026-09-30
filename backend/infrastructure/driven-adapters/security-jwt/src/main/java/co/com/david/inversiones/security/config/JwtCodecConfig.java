package co.com.david.inversiones.security.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Wires the HS256 {@link JwtEncoder}/{@link JwtDecoder} pair used to issue and validate bearer
 * tokens, both backed by Nimbus (via {@code spring-security-oauth2-jose}) over a single shared
 * secret.
 *
 * <p>The secret is validated to be at least 32 bytes here, in the constructor, so a misconfigured
 * secret fails application context startup rather than the first login attempt: a deployment that
 * boots successfully and only then rejects every login looks healthy to a health check, which is
 * the worst place for this kind of mistake to surface.
 *
 * <p>This class lives under {@code config} and is excluded from the module coverage gate by
 * convention (see {@code main.gradle}), consistent with the other Spring wiring classes in this
 * codebase.
 */
@Configuration
public class JwtCodecConfig {

    /** HS256 requires a key of at least 256 bits (32 bytes); see RFC 7518 section 3.2. */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;

    public JwtCodecConfig(@Value("${inversiones.security.jwt-secret}") String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "inversiones.security.jwt-secret must be at least " + MIN_SECRET_BYTES
                            + " bytes for HS256 signing, but was " + secretBytes.length + " bytes");
        }
        this.secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefault());
        return decoder;
    }
}
