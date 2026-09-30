package co.com.david.inversiones.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptPasswordHasherAdapterTest {

    private final BCryptPasswordHasherAdapter passwordHasher = new BCryptPasswordHasherAdapter();

    @Test
    void hashProducedByHashVerifiesWithMatches() {
        String hash = passwordHasher.hash("Inversiones2026!");

        assertThat(passwordHasher.matches("Inversiones2026!", hash)).isTrue();
        assertThat(passwordHasher.matches("wrong-password", hash)).isFalse();
    }

    /**
     * The seed migration (V2__seed_reference_data.sql) stores a BCrypt hash produced by
     * {@code BCryptPasswordEncoder} for the local user "david". If this adapter used a different
     * algorithm or a different encoder configuration, that seed and this adapter would disagree and
     * login would be impossible in production despite every unit test passing.
     */
    @Test
    void verifiesTheHashSeededInTheReferenceDataMigration() {
        String seededHash = "$2a$10$8h4HIXTmlqYgxHHTXt9vi.Tg1neXgrGmgZcv06pcahzg2CztxVUZ6";

        assertThat(passwordHasher.matches("Inversiones2026!", seededHash)).isTrue();
        assertThat(passwordHasher.matches("not-the-password", seededHash)).isFalse();
    }
}
