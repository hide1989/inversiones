package co.com.david.inversiones.model.exception;

import co.com.david.inversiones.model.Ticker;

/**
 * Raised when a transaction references a catalog entry that does not exist: an instrument (by
 * ticker) or, equally, a broker (by name). Both are "this transaction points at something the
 * catalog does not have" — the same failure mode from the adapter's point of view — so a broker
 * lookup miss reuses this type via {@link #forUnknownBroker(String)} rather than introducing a
 * sibling exception for what is the same condition under a different foreign key.
 */
public class UnknownInstrumentException extends DomainException {

    public UnknownInstrumentException(Ticker ticker) {
        super("Unknown instrument: %s".formatted(ticker.symbol()));
    }

    private UnknownInstrumentException(String message) {
        super(message);
    }

    /** @return an exception for a transaction referencing a broker name absent from the catalog */
    public static UnknownInstrumentException forUnknownBroker(String brokerName) {
        return new UnknownInstrumentException("Unknown broker: %s".formatted(brokerName));
    }
}
