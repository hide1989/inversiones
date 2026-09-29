package co.com.david.inversiones.model.gateways;

/**
 * Port for hashing and verifying passwords.
 *
 * <p>The domain never learns which algorithm is behind this port.
 */
public interface PasswordHasher {

    /** Returns the hash to store for this raw password. */
    String hash(String rawPassword);

    /**
     * Returns whether the raw password matches the stored hash.
     *
     * <p>The implementation must compare in constant time: leaking how many characters matched
     * through timing would defeat the point of hashing the password in the first place.
     */
    boolean matches(String rawPassword, String passwordHash);
}
