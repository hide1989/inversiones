package co.com.david.inversiones.model.exception;

import co.com.david.inversiones.model.Currency;

/** Raised when an arithmetic operation mixes two different currencies. */
public class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException(Currency left, Currency right) {
        super("Cannot operate on amounts in different currencies: %s and %s".formatted(left, right));
    }
}
