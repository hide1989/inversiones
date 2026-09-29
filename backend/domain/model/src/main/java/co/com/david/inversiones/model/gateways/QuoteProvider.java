package co.com.david.inversiones.model.gateways;

import co.com.david.inversiones.model.Quote;
import co.com.david.inversiones.model.Ticker;

import java.util.Map;
import java.util.Set;

/** Source of current market prices. */
public interface QuoteProvider {

    /**
     * Current quotes for the requested tickers.
     *
     * <p>May return fewer entries than requested: a source with no data for a ticker omits it rather
     * than returning a placeholder price.
     */
    Map<Ticker, Quote> findQuotes(Set<Ticker> tickers);
}
