package co.com.david.inversiones.model.exception;

import co.com.david.inversiones.model.Ticker;

/**
 * Raised when a transaction references a catalog entry that does not exist — an instrument by ticker,
 * or a broker by name.
 *
 * <p>One type covers both because the domain treats them identically: the write is rejected either
 * way. The message names which entry was missing, so a caller needs no separate type to act on it.
 */
public class UnknownCatalogEntryException extends DomainException {

    private UnknownCatalogEntryException(String message) {
        super(message);
    }

    /** @return an exception for a transaction referencing a ticker absent from the catalog */
    public static UnknownCatalogEntryException forInstrument(Ticker ticker) {
        return new UnknownCatalogEntryException("Unknown instrument: %s".formatted(ticker.symbol()));
    }

    /** @return an exception for a transaction referencing a broker name absent from the catalog */
    public static UnknownCatalogEntryException forBroker(String brokerName) {
        return new UnknownCatalogEntryException("Unknown broker: %s".formatted(brokerName));
    }
}
