package co.com.david.inversiones.jpa.user;

import co.com.david.inversiones.model.User;

/**
 * Hand-written mapping between {@link UserData} and {@link User}.
 *
 * <p>Only two columns, but still not reflection-based: this module's convention (established in
 * {@code TransactionMapper}) is that no JPA entity crosses the port boundary, and a generic mapper
 * would need to be told that anyway. Kept explicit for consistency with the other mappers in this
 * module.
 */
final class UserMapper {

    private UserMapper() {
    }

    static User toDomain(UserData data) {
        return User.builder()
                .id(data.getId())
                .username(data.getUsername())
                .passwordHash(data.getPasswordHash())
                .build();
    }
}
