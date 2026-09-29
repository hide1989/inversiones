package co.com.david.inversiones.model.exception;

/** Raised when authentication fails, without revealing which half of the credentials was wrong. */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
