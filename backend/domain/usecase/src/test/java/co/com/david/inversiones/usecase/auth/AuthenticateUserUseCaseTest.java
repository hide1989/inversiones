package co.com.david.inversiones.usecase.auth;

import co.com.david.inversiones.model.AuthenticatedSession;
import co.com.david.inversiones.model.User;
import co.com.david.inversiones.model.exception.InvalidCredentialsException;
import co.com.david.inversiones.model.gateways.PasswordHasher;
import co.com.david.inversiones.model.gateways.TokenProvider;
import co.com.david.inversiones.model.gateways.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserUseCaseTest {

    private static final String USERNAME = "david";
    private static final String PASSWORD = "correct-horse";
    private static final String HASH = "$2a$10$storedhash";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private TokenProvider tokenProvider;
    @InjectMocks
    private AuthenticateUserUseCase useCase;

    private static User user() {
        return User.builder().id(1L).username(USERNAME).passwordHash(HASH).build();
    }

    @Test
    void issuesASessionForValidCredentials() {
        AuthenticatedSession session =
                new AuthenticatedSession("jwt-token", Instant.parse("2026-09-30T00:00:00Z"), USERNAME);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user()));
        when(passwordHasher.matches(PASSWORD, HASH)).thenReturn(true);
        when(tokenProvider.issue(any(User.class))).thenReturn(session);

        assertThat(useCase.execute(USERNAME, PASSWORD)).isSameAs(session);
    }

    @Test
    void rejectsAWrongPassword() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user()));
        when(passwordHasher.matches("wrong", HASH)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(USERNAME, "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokenProvider, never()).issue(any());
    }

    @Test
    void rejectsAnUnknownUsernameWithTheSameMessageAsAWrongPassword() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user()));
        when(passwordHasher.matches("wrong", HASH)).thenReturn(false);

        String unknownUser = catchMessage(() -> useCase.execute("ghost", PASSWORD));
        String wrongPassword = catchMessage(() -> useCase.execute(USERNAME, "wrong"));

        assertThat(unknownUser).isEqualTo(wrongPassword);
    }

    @Test
    void doesNotConsultTheHasherWhenTheUserDoesNotExist() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("ghost", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(passwordHasher, tokenProvider);
    }

    private static String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected InvalidCredentialsException");
        } catch (InvalidCredentialsException exception) {
            return exception.getMessage();
        }
    }
}
