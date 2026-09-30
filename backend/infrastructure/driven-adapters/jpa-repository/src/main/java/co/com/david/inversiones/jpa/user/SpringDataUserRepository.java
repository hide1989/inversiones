package co.com.david.inversiones.jpa.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data repository for {@link UserData}. */
public interface SpringDataUserRepository extends JpaRepository<UserData, Long> {

    /** Looks a user up by their exact, unique username. */
    Optional<UserData> findByUsername(String username);
}
