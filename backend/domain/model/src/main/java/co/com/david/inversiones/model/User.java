package co.com.david.inversiones.model;

import java.util.Objects;

/**
 * A person who can authenticate into the system.
 *
 * <p>{@link #toString()} deliberately exposes only the id and username: a stray log line must
 * never be able to leak {@link #passwordHash()}.
 */
public final class User {

    private final Long id;
    private final String username;
    private final String passwordHash;

    private User(Builder builder) {
        this.id = builder.id;
        this.username = Objects.requireNonNull(builder.username, "username must not be null");
        this.passwordHash = Objects.requireNonNull(builder.passwordHash, "passwordHash must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }

    /** Exposes only the id and username; never the password hash. */
    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "'}";
    }

    /** Fluent builder for {@link User}. */
    public static final class Builder {

        private Long id;
        private String username;
        private String passwordHash;

        private Builder() {
        }

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public User build() {
            return new User(this);
        }
    }
}
