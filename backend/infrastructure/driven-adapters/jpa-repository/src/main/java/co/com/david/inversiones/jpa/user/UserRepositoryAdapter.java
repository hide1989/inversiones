package co.com.david.inversiones.jpa.user;

import co.com.david.inversiones.model.User;
import co.com.david.inversiones.model.gateways.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA-backed implementation of {@link UserRepository}.
 *
 * <p>No {@link UserData} is ever returned from a public method here; every result passes through
 * {@link UserMapper} before it leaves this class.
 */
@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository userRepository;

    public UserRepositoryAdapter(SpringDataUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Returns {@link Optional#empty()} for an unknown username; never {@code null}. */
    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username).map(UserMapper::toDomain);
    }
}
