package co.com.david.inversiones.model.exception;

import co.com.david.inversiones.model.Ticker;

/**
 * Raised when an upstream provider could not resolve a ticker at all — as opposed to resolving it
 * and reporting no data for it, which is a valid {@code hasData: false} result, not an error.
 *
 * <p>The fundamentals use case returns an empty result for exactly this case ("n8n could not
 * analyze it at all", per its provider's Javadoc); the entry point is what turns that into this
 * exception, since "not found" is an HTTP concern the use case itself has no opinion on.
 */
public class UnresolvedTickerException extends DomainException {

    public UnresolvedTickerException(Ticker ticker) {
        super("Could not resolve ticker: %s".formatted(ticker.symbol()));
    }
}
