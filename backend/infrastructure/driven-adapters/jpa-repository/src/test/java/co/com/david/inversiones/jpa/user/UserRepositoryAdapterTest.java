package co.com.david.inversiones.jpa.user;

import co.com.david.inversiones.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link UserRepositoryAdapter} against a real PostgreSQL instance, with
 * Flyway applying the actual migrations (schema plus the seeded local user) so the tests exercise
 * the same user the running application starts with.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserRepositoryAdapter.class)
class UserRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private UserRepositoryAdapter adapter;

    @Test
    void findByUsernameReturnsTheSeededDavidUser() {
        Optional<User> found = adapter.findByUsername("david");

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().username()).isEqualTo("david");
    }

    @Test
    void findByUsernameOnAnUnknownUsernameReturnsEmptyOptional() {
        Optional<User> found = adapter.findByUsername("nobody");

        assertThat(found).isEmpty();
    }

    @Test
    void aFoundUserToStringNeverContainsThePasswordHash() {
        User found = adapter.findByUsername("david").orElseThrow();

        assertThat(found.toString()).doesNotContain(found.passwordHash());
        assertThat(found.toString()).doesNotContain("$2a$");
    }
}
