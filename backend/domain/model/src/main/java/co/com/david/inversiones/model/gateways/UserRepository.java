package co.com.david.inversiones.model.gateways;

import co.com.david.inversiones.model.User;

import java.util.Optional;

/** Persistence port for {@link User} records. */
public interface UserRepository {

    /** Returns the user with this username, if one exists. */
    Optional<User> findByUsername(String username);
}
