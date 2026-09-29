package co.com.david.inversiones.model.exception;

/** Base type for every error raised by a business rule of the domain. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
