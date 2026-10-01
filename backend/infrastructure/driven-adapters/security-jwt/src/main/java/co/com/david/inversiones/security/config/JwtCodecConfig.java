package co.com.david.inversiones.security.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
 * <p><b>The committed local default cannot pass this check outside the {@code local} profile.</b>
 * {@code application.yml} used to default {@code jwt-secret} to a 40-byte literal
 * ({@value #COMMITTED_LOCAL_DEFAULT}) that satisfied the 32-byte minimum above, so a deployment
 * that simply forgot to set {@code JWT_SECRET} booted successfully and signed every token with a
 * key published in this repository — the length check gave false assurance about a value it never
 * questioned. The default now lives only in {@code application-local.yml}, so any other profile
 * fails to resolve the property at all when {@code JWT_SECRET} is unset; this constructor also
 * rejects the literal value outright whenever the active profile is not {@code local}, in case it
 * is ever copy-pasted directly into a non-local {@code JWT_SECRET} by mistake. Local development
 * needs no manual step: {@code application-local.yml} supplies the same default, and the
 * {@code local} profile is Spring's own default when none is set (see {@code application.yml}'s
 * {@code spring.profiles.default}), so this guard never fires for the setup this project actually
 * ships with.
 *
 * <p>This class lives under {@code config} and is excluded from the module coverage gate by
 * convention (see {@code main.gradle}), consistent with the other Spring wiring classes in this
 * codebase.
 */
@Configuration
public class JwtCodecConfig {

    /** HS256 requires a key of at least 256 bits (32 bytes); see RFC 7518 section 3.2. */
    private static final int MIN_SECRET_BYTES = 32;

    /**
     * The value {@code application-local.yml} defaults {@code jwt-secret} to. Public by
     * construction (it is committed to this repository and exists only so local development needs
     * no manual setup step), so it must never be accepted as a real signing key.
     */
    static final String COMMITTED_LOCAL_DEFAULT = "local-development-secret-change-me-32bytes";

    private static final String LOCAL_PROFILE = "local";

    private final SecretKey secretKey;

    public JwtCodecConfig(@Value("${inversiones.security.jwt-secret}") String secret, Environment environment) {
        if (COMMITTED_LOCAL_DEFAULT.equals(secret) && !environment.acceptsProfiles(Profiles.of(LOCAL_PROFILE))) {
            throw new IllegalStateException(
                    "inversiones.security.jwt-secret is the committed local-development default, which is "
                            + "public in this repository; set JWT_SECRET to a real secret for any deployment "
                            + "outside the '" + LOCAL_PROFILE + "' profile");
        }
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
