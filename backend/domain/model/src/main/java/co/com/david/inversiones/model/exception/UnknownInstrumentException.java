package co.com.david.inversiones.model.exception;

import co.com.david.inversiones.model.Ticker;

/** Raised when a transaction references an instrument that is not in the catalog. */
public class UnknownInstrumentException extends DomainException {

    public UnknownInstrumentException(Ticker ticker) {
        super("Unknown instrument: %s".formatted(ticker.symbol()));
    }
}
