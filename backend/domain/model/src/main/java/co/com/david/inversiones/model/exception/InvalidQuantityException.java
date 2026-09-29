package co.com.david.inversiones.model.exception;

/** Raised when a quantity is negative or an operation would make it negative. */
public class InvalidQuantityException extends DomainException {

    public InvalidQuantityException(String message) {
        super(message);
    }
}
