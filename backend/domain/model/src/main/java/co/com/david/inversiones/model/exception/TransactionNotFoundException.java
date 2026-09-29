package co.com.david.inversiones.model.exception;

/** Raised when a transaction id does not exist. */
public class TransactionNotFoundException extends DomainException {

    public TransactionNotFoundException(Long id) {
        super("No transaction with id %s".formatted(id));
    }
}
