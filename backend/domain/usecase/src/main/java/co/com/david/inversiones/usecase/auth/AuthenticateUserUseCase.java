package co.com.david.inversiones.usecase.auth;

import co.com.david.inversiones.model.AuthenticatedSession;
import co.com.david.inversiones.model.User;
import co.com.david.inversiones.model.exception.InvalidCredentialsException;
import co.com.david.inversiones.model.gateways.PasswordHasher;
import co.com.david.inversiones.model.gateways.TokenProvider;
import co.com.david.inversiones.model.gateways.UserRepository;

/**
 * Verifies credentials and issues a session.
 *
 * <p>An unknown username and a wrong password fail identically, with the same exception and the same
 * message: telling them apart would let a caller enumerate which usernames exist.
 */
public class AuthenticateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenProvider tokenProvider;

    public AuthenticateUserUseCase(UserRepository userRepository,
                                   PasswordHasher passwordHasher,
                                   TokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Authenticates a user and issues a session.
     *
     * @throws InvalidCredentialsException when the username is unknown or the password does not match
     */
    public AuthenticatedSession execute(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordHasher.matches(rawPassword, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        return tokenProvider.issue(user);
    }
}
