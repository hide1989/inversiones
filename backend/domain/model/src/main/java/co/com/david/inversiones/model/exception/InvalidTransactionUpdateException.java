package co.com.david.inversiones.model.exception;

import co.com.david.inversiones.model.Transaction;

/** Raised when an edit would move a trade to a different instrument or broker. */
public class InvalidTransactionUpdateException extends DomainException {

    public InvalidTransactionUpdateException(Transaction existing, Transaction replacement) {
        super("Cannot move a trade from %s at %s to %s at %s: delete it and record a new one instead"
                .formatted(existing.ticker().symbol(), existing.brokerName(),
                        replacement.ticker().symbol(), replacement.brokerName()));
    }
}
