package co.com.david.inversiones.model.exception;

import co.com.david.inversiones.model.Ticker;

/** Raised when a sale would remove more shares than the position holds. */
public class InsufficientPositionException extends DomainException {

    public InsufficientPositionException(Ticker ticker, String brokerName, String held, String requested) {
        super("Cannot sell %s shares of %s at %s: only %s held"
                .formatted(requested, ticker.symbol(), brokerName, held));
    }
}
