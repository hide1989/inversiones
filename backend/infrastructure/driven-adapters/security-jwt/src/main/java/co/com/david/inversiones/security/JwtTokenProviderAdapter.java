package co.com.david.inversiones.security;

import co.com.david.inversiones.model.AuthenticatedSession;
import co.com.david.inversiones.model.User;
import co.com.david.inversiones.model.gateways.TimeProvider;
import co.com.david.inversiones.model.gateways.TokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * {@link TokenProvider} that issues HS256 JSON Web Tokens through Spring Security's Nimbus-based
 * {@link JwtEncoder}.
 *
 * <p>The token's {@code sub} claim is the username, and it carries {@code iat}/{@code exp}. The
 * instant used for both is read through {@link TimeProvider} rather than {@link Instant#now()}:
 * that is what lets a test drive expiry by advancing a fixed clock instead of sleeping for real.
 */
@Component
public class JwtTokenProviderAdapter implements TokenProvider {

    private final JwtEncoder jwtEncoder;
    private final TimeProvider timeProvider;
    private final Duration expiration;

    public JwtTokenProviderAdapter(JwtEncoder jwtEncoder,
                                    TimeProvider timeProvider,
                                    @Value("${inversiones.security.jwt-expiration-minutes}") long expirationMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.timeProvider = timeProvider;
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    @Override
    public AuthenticatedSession issue(User user) {
        Instant issuedAt = timeProvider.now();
        Instant expiresAt = issuedAt.plus(expiration);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.username())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(header, claims));

        return new AuthenticatedSession(jwt.getTokenValue(), expiresAt, user.username());
    }
}
