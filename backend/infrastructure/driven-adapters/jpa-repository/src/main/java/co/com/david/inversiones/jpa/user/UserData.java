package co.com.david.inversiones.jpa.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * JPA entity for the {@code users} table.
 *
 * <p>This entity is never returned from a public method on {@link UserRepositoryAdapter}: every
 * result passes through {@link UserMapper} first, so a stray log or serialization of a domain
 * {@link co.com.david.inversiones.model.User} can rely on that type's own {@code toString()}
 * override, rather than on this JPA entity (which has no such protection) never leaking.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Set by the database default; never written by this adapter. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
