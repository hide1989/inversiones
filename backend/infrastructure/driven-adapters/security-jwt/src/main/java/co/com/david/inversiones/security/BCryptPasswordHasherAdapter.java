package co.com.david.inversiones.security;

import co.com.david.inversiones.model.gateways.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * {@link PasswordHasher} backed by {@link BCryptPasswordEncoder}.
 *
 * <p>{@link #matches(String, String)} delegates entirely to the encoder: its comparison is already
 * constant-time, so there is nothing for this adapter to add, and the domain never learns which
 * algorithm is behind the port.
 */
@Component
public class BCryptPasswordHasherAdapter implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }
}
